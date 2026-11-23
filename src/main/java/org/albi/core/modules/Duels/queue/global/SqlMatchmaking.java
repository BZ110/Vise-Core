package org.albi.core.modules.Duels.queue.global;

import org.albi.core.data.SqlNetworkState;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.albi.core.modules.Duels.queue.global.RedisMatchmaking.*;

/** A coordinated MySQL fleet has one assignment authority, including while Redis is healthy. */
final class SqlMatchmaking {
    private static final int MAX_TICKETS = 512;
    private static final int MAX_HOSTS = 64;
    private static final int MAX_CUSTOM_BYTES = 16 * 1024;

    private record LiveTicket(Entry entry, long expires) {}
    private record LiveHost(MatchmakingPolicy.Host host, long expires) {}

    private static final class Match {
        Assignment offer;
        long expires;
        String state = "offered";
        boolean votedFirst;
        boolean votedSecond;
        boolean departedFirst;
        boolean departedSecond;

        Match(Assignment offer, long expires) { this.offer = offer; this.expires = expires; }

        Assignment snapshot(long now) {
            return new Assignment(offer.id(), offer.first(), offer.second(), offer.host(), offer.kit(),
                    offer.firstKit(), offer.secondKit(), state, expires - now, departedFirst, departedSecond);
        }
    }

    private static final class Pool {
        Map<UUID, LiveTicket> tickets = new HashMap<>();
        Map<String, LiveHost> hosts = new HashMap<>();
        Map<String, Match> matches = new HashMap<>();
        long nextScan;
        int cursor;
    }

    private final SqlNetworkState sql;
    private final MatchmakingSettings settings;
    private final MatchmakingPolicy policy;
    private final String key;

    SqlMatchmaking(SqlNetworkState sql, MatchmakingSettings settings, MatchmakingPolicy policy) {
        this.sql = sql;
        this.settings = settings;
        this.policy = policy;
        key = "matchmaking:" + settings.namespace();
    }

    void prepare() throws SQLException { sql.prepare(key); }

    List<Assignment> exchange(String session, MatchmakingPolicy.Host heartbeat, List<Entry> entries,
                              List<Change> changes) throws SQLException {
        return sql.transact(key, Pool.class, Pool::new, (pool, now) -> {
            pool.tickets.values().removeIf(ticket -> ticket.expires <= now);
            pool.hosts.values().removeIf(host -> host.expires <= now);
            pool.matches.values().removeIf(match -> match.expires <= now);
            Map<UUID, Match> claims = new HashMap<>();
            pool.matches.values().forEach(match -> {
                claims.put(match.offer.first().player(), match);
                claims.put(match.offer.second().player(), match);
            });
            for (Change change : changes) apply(pool, claims, change, session, now);
            registerHost(pool, heartbeat, session, now);
            for (Entry entry : entries) {
                UUID player = entry.ticket().player();
                if (claims.containsKey(player) || (!pool.tickets.containsKey(player) && pool.tickets.size() >= MAX_TICKETS)) continue;
                var existing = pool.tickets.get(player);
                if (existing != null && !existing.entry.ticket().server().equals(settings.server())) continue;
                String custom = entry.custom();
                if (custom != null && (custom.length() > MAX_CUSTOM_BYTES / 4
                        && custom.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CUSTOM_BYTES)) {
                    custom = null;
                }
                pool.tickets.put(player, new LiveTicket(new Entry(entry.ticket(), custom), now + TICKET_LEASE_MS));
            }
            if (now >= pool.nextScan) {
                pool.nextScan = now + Math.max(500, settings.pollTicks() * 50L);
                match(pool, claims, now);
            }
            return pool.matches.values().stream().filter(m -> relevant(m.offer, session)).map(m -> m.snapshot(now)).toList();
        });
    }

    private void registerHost(Pool pool, MatchmakingPolicy.Host heartbeat, String session, long now) {
        if (heartbeat == null) return;
        var previous = pool.hosts.get(settings.server());
        if (previous != null && !previous.host.session().equals(session)) return;
        if (previous != null || pool.hosts.size() < MAX_HOSTS) {
            pool.hosts.put(settings.server(), new LiveHost(heartbeat, now + TICKET_LEASE_MS));
        }
    }

    private boolean relevant(Assignment match, String session) {
        return (match.host().server().equals(settings.server()) && match.host().session().equals(session))
                || match.first().server().equals(settings.server()) || match.second().server().equals(settings.server());
    }

    private void match(Pool pool, Map<UUID, Match> claims, long now) {
        var all = pool.tickets.values().stream().filter(t -> !claims.containsKey(t.entry.ticket().player()))
                .map(t -> t.entry.ticket()).sorted(Comparator.comparingLong(MatchmakingPolicy.Ticket::since)
                        .thenComparing(MatchmakingPolicy.Ticket::player)).toList();
        if (all.size() < 2) return;
        int anchors = Math.min(all.size(), settings.scanSize() / 8);
        int cursor = Math.floorMod(pool.cursor, all.size());
        int count = Math.min(settings.scanSize(), all.size());
        var sample = new java.util.LinkedHashSet<>(all.subList(0, anchors));
        for (int step = 0; step < count && sample.size() < count; step++) sample.add(all.get((cursor + step) % all.size()));
        pool.cursor = (cursor + Math.max(1, count - anchors)) % all.size();
        var candidates = new ArrayList<>(sample);
        candidates.sort(Comparator.comparingLong(MatchmakingPolicy.Ticket::since));
        Map<String, Long> pending = new HashMap<>();
        for (Match match : pool.matches.values()) pending.merge(match.offer.host().server(), 1L, Long::sum);
        int offered = 0;
        for (var first : List.copyOf(candidates)) {
            if (claims.containsKey(first.player()) || offered >= settings.offersPerPoll()) continue;
            var hosts = pool.hosts.values().stream().map(LiveHost::host)
                    .filter(host -> pending.getOrDefault(host.server(), 0L) < settings.pendingLimit()).toList();
            var chosen = policy.best(first, candidates, hosts, now, false);
            if (chosen == null) continue;
            var second = chosen.second();
            String firstKit = null;
            String secondKit = null;
            if (MatchmakingPolicy.PERSONAL.equals(chosen.kit())) {
                firstKit = pool.tickets.get(first.player()).entry.custom();
                secondKit = pool.tickets.get(second.player()).entry.custom();
                if (firstKit == null || secondKit == null) continue;
            }
            String id = UUID.randomUUID().toString();
            var offer = new Assignment(id, first, second, chosen.host(), chosen.kit(), firstKit, secondKit,
                    "offered", MATCH_LEASE_MS, false, false);
            var match = new Match(offer, now + MATCH_LEASE_MS);
            pool.matches.put(id, match);
            pending.merge(chosen.host().server(), 1L, Long::sum);
            claims.put(first.player(), match);
            claims.put(second.player(), match);
            candidates.remove(first);
            candidates.remove(second);
            offered++;
        }
    }

    private void apply(Pool pool, Map<UUID, Match> claims, Change change, String session, long now) {
        if ("cancel".equals(change.action())) {
            var live = pool.tickets.get(change.player());
            if (live != null && owns(live.entry.ticket(), change)) pool.tickets.remove(change.player());
            var match = claims.get(change.player());
            if (match != null && !"started".equals(match.state)
                    && (owns(match.offer.first(), change) || owns(match.offer.second(), change))) {
                clear(pool, claims, match, "ready".equals(match.state));
            }
            return;
        }
        var match = pool.matches.get(change.match());
        if (match == null) return;
        var host = match.offer.host();
        var live = pool.hosts.get(host.server());
        boolean activeHost = live != null && host.session().equals(live.host.session());
        if (settings.server().equals(host.server()) && session.equals(host.session())) {
            switch (change.action()) {
                case "reserve" -> { if ("offered".equals(match.state)) match.state = "reserved"; }
                case "abort" -> { if (!"started".equals(match.state)) clear(pool, claims, match, "ready".equals(match.state)); }
                case "finish" -> { if ("started".equals(match.state)) clear(pool, claims, match, true); }
                case "start" -> {
                    if (activeHost && "ready".equals(match.state) && match.departedFirst && match.departedSecond) match.state = "started";
                }
                default -> { }
            }
        }
        if (!activeHost) return;
        boolean first = owns(match.offer.first(), change);
        if (!first && !owns(match.offer.second(), change)) return;
        if ("vote".equals(change.action()) && "reserved".equals(match.state) && match.expires - now >= MIN_READY_MS) {
            if (first) match.votedFirst = true;
            else match.votedSecond = true;
            if (match.votedFirst && match.votedSecond) match.state = "ready";
        }
        if ("depart".equals(change.action()) && "ready".equals(match.state) && match.expires - now >= MIN_DEPART_MS) {
            if (first) match.departedFirst = true;
            else match.departedSecond = true;
            removeTicket(pool, first ? match.offer.first() : match.offer.second());
        }
    }

    private boolean owns(MatchmakingPolicy.Ticket ticket, Change change) {
        return ticket.player().equals(change.player()) && ticket.token().equals(change.token())
                && ticket.server().equals(settings.server());
    }

    private static void clear(Pool pool, Map<UUID, Match> claims, Match match, boolean removeTickets) {
        for (var ticket : List.of(match.offer.first(), match.offer.second())) {
            if (removeTickets) removeTicket(pool, ticket);
            claims.remove(ticket.player(), match);
        }
        pool.matches.remove(match.offer.id());
    }

    private static void removeTicket(Pool pool, MatchmakingPolicy.Ticket ticket) {
        pool.tickets.computeIfPresent(ticket.player(), (id, live) -> live.entry.ticket().token().equals(ticket.token()) ? null : live);
    }
}
