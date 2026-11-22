package org.albi.core.modules.Duels.queue.global;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class RedisMatchmaking {
    static final int TICKET_LEASE_MS = 6_000;
    static final int MATCH_LEASE_MS = 25_000;
    static final int FALLBACK_FENCE_MS = TICKET_LEASE_MS + MATCH_LEASE_MS;
    static final int MIN_READY_MS = 9_000;
    static final int MIN_DEPART_MS = 5_000;
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(MatchmakingPolicy.Host.class,
                    (JsonDeserializer<MatchmakingPolicy.Host>) (json, type, context) -> {
                        HostData host = context.deserialize(json, HostData.class);
                        return new MatchmakingPolicy.Host(host.server, host.session, host.pool, kits(host.casual),
                                kits(host.ranked), host.players, host.capacity, host.arenas);
                    }).create();
    private static final String SCRIPT = loadScript();
    private static final String SHA = scriptSha();

    record Entry(MatchmakingPolicy.Ticket ticket, String custom) {}

    record Change(String action, String match, UUID player, String token) {
        static Change host(String action, String match) {
            return new Change(action, match, null, null);
        }

        static Change source(String action, String match, MatchmakingPolicy.Ticket ticket) {
            return new Change(action, match, ticket.player(), ticket.token());
        }
    }

    record Assignment(String id, MatchmakingPolicy.Ticket first, MatchmakingPolicy.Ticket second,
                      MatchmakingPolicy.Host host, String kit, String firstKit, String secondKit,
                      String state, long remaining, boolean departedFirst, boolean departedSecond) {}

    private record Poll(String action, String server, String session, int ticketLease, int minReady, int minDepart,
                        MatchmakingPolicy.Host host, List<Entry> entries, List<Change> changes) {}

    private record Scan(String action, String session, int scanSize, int scanLease, int ticketLease) {}
    private record Offer(String action, int limit, int matchLease, Assignment offer) {}
    private record Sample(JsonElement tickets, JsonElement hosts, long now) {}
    private record HostData(String server, String session, String pool, JsonElement casual, JsonElement ranked,
                            int players, int capacity, int arenas) {}

    private final MatchmakingSettings settings;
    private final MatchmakingPolicy policy;
    private final List<String> keys;

    RedisMatchmaking(MatchmakingSettings settings, MatchmakingPolicy policy) {
        this.settings = settings;
        this.policy = policy;
        keys = List.of("vise:mm:v1:{" + settings.namespace() + "}:");
    }

    List<Assignment> exchange(Jedis redis, String session, MatchmakingPolicy.Host host, List<Entry> entries,
                              List<Change> changes) {
        var poll = new Poll("poll", settings.server(), session, TICKET_LEASE_MS,
                MIN_READY_MS, MIN_DEPART_MS, host, entries, changes);
        return List.of(JSON.fromJson((String) eval(redis, poll), Assignment[].class));
    }

    void findMatches(Jedis redis, String session) {
        int intervalMs = settings.pollTicks() * 50;
        var scan = new Scan("scan", session, settings.scanSize(), Math.max(250, intervalMs - 50), TICKET_LEASE_MS);
        var sample = JSON.fromJson((String) eval(redis, scan), Sample.class);
        if (!sample.tickets.isJsonArray() || !sample.hosts.isJsonArray()) return;

        var candidates = new ArrayList<>(List.of(JSON.fromJson(sample.tickets, MatchmakingPolicy.Ticket[].class)));
        candidates.sort(java.util.Comparator.comparingLong(MatchmakingPolicy.Ticket::since));
        var hosts = List.of(JSON.fromJson(sample.hosts, MatchmakingPolicy.Host[].class));
        Set<UUID> assigned = new HashSet<>();
        int offersRemaining = settings.offersPerPoll();

        for (var first : List.copyOf(candidates)) {
            if (assigned.contains(first.player())) continue;
            var match = policy.best(first, candidates, hosts, sample.now, false);
            if (match == null) continue;

            var offer = new Assignment(UUID.randomUUID().toString(), match.first(), match.second(),
                    match.host(), match.kit(), null, null, null, 0, false, false);
            if ((long) eval(redis, new Offer("offer", settings.pendingLimit(), MATCH_LEASE_MS, offer)) == 1) {
                assigned.add(first.player());
                assigned.add(match.second().player());
                candidates.remove(match.first());
                candidates.remove(match.second());
            }
            if (--offersRemaining == 0) return;
        }
    }

    private Object eval(Jedis redis, Object request) {
        List<String> arguments = List.of(JSON.toJson(request));
        try {
            return redis.evalsha(SHA, keys, arguments);
        } catch (JedisNoScriptException missing) {
            return redis.eval(SCRIPT, keys, arguments);
        }
    }

    private static Set<String> kits(JsonElement encoded) {
        // Redis Lua's cjson encodes an empty array as an empty object.
        Set<String> kits = new LinkedHashSet<>();
        if (encoded != null && encoded.isJsonArray()) {
            encoded.getAsJsonArray().forEach(kit -> kits.add(kit.getAsString()));
        }
        return kits;
    }

    private static String loadScript() {
        try (InputStream resource = RedisMatchmaking.class.getResourceAsStream("/matchmaking/exchange.lua")) {
            return new String(Objects.requireNonNull(resource).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read matchmaking Redis script", e);
        }
    }

    private static String scriptSha() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(SCRIPT.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash matchmaking Redis script", e);
        }
    }
}
