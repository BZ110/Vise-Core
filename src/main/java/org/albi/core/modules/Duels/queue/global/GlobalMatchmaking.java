package org.albi.core.modules.Duels.queue.global;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Tournaments.TournamentManager;
import org.albi.core.util.ProxyTransfer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.albi.core.modules.Duels.queue.global.RedisMatchmaking.Assignment;
import static org.albi.core.modules.Duels.queue.global.RedisMatchmaking.Change;
import static org.albi.core.modules.Duels.queue.global.RedisMatchmaking.Entry;

/** Owns local queue state on the tick thread. Redis I/O works on immutable snapshots off-thread. */
public final class GlobalMatchmaking {
    private static final class Waiting {
        final String token = UUID.randomUUID().toString();
        final Player player;
        final String kit;
        final boolean ranked;
        final long since = System.currentTimeMillis();
        final String custom;

        Waiting(Player player, String kit, boolean ranked, String custom) {
            this.player = player;
            this.kit = kit;
            this.ranked = ranked;
            this.custom = custom;
        }
    }

    private record Hosted(Assignment match, QueueDuelHost.Reservation reservation, long expires) {}
    private record Transfer(String match, long expires) {}
    private record LocalMatch(Waiting first, Waiting second, QueueDuelHost.Reservation reservation, long expires) {}
    private record SaveBeforeTransfer(StatManagement.PlayerStat stat, MainPlayer profile) {}
    private record Exchange(boolean mayHavePublished, long sentAt, List<Assignment> assignments) {}

    private final Core plugin;
    private final MatchmakingSettings settings;
    private final MatchmakingPolicy policy;
    private final QueueDuelHost host;
    private final RedisMatchmaking redis;
    private final String session = UUID.randomUUID().toString();
    private final Map<UUID, Waiting> waiting = new LinkedHashMap<>();
    private final Map<UUID, Long> publicationFences = new HashMap<>();
    private final Map<String, Hosted> hosted = new HashMap<>();
    private final Map<UUID, Transfer> transfers = new HashMap<>();
    private final Map<UUID, Player> arrivals = new HashMap<>();
    private final List<LocalMatch> localMatches = new ArrayList<>();
    private final Set<Change> changes = new java.util.LinkedHashSet<>();
    private final BukkitTask ticker;
    private boolean redisAvailable = true;
    private boolean exchanging;
    private boolean closed;

    public static GlobalMatchmaking create(Core plugin) {
        MatchmakingSettings settings = MatchmakingSettings.load(plugin);
        if (settings == null) return null;
        return new GlobalMatchmaking(plugin, settings);
    }

    private GlobalMatchmaking(Core plugin, MatchmakingSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        policy = new MatchmakingPolicy(settings.rules());
        host = new QueueDuelHost(plugin);
        redis = new RedisMatchmaking(settings, policy);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, settings.pollTicks());
        plugin.getLogger().info("[Matchmaking] Redis queues enabled for " + settings.server() + " in " + settings.pool() + ".");
    }

    public boolean join(Player player, String kit, boolean ranked) {
        UUID id = player.getUniqueId();
        if (hasPendingMatch(player)) {
            player.sendMessage("§eYour queued match is already being prepared.");
            return false;
        }
        var spectators = DuelManager.getSpectators();
        if (spectators != null && spectators.isTransitioning(player)) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_transfer_busy");
            return false;
        }
        if (!eligible(player, ranked)) {
            player.sendMessage("§cReturn to the lobby and finish your current activity before joining the queue.");
            return false;
        }

        String custom = null;
        if (MatchmakingPolicy.PERSONAL.equals(kit) || MatchmakingPolicy.QUICK.equals(kit)) {
            CustomKit selected = selectedKit(player);
            if (selected != null) custom = selected.toJson();
            if (MatchmakingPolicy.PERSONAL.equals(kit) && custom == null) {
                player.sendMessage("§cSelect a personal kit before joining that queue.");
                return false;
            }
        }

        Waiting previous = waiting.get(id);
        if (previous != null) drop(previous, true);
        waiting.put(id, new Waiting(player, kit, ranked, custom));
        return true;
    }

    public void leave(Player player, boolean ranked) {
        Waiting current = waiting.get(player.getUniqueId());
        if (current != null && current.ranked == ranked) drop(current, true);
    }

    public boolean hasPendingMatch(Player player) {
        UUID id = player.getUniqueId();
        return transfers.containsKey(id) || reservedFor(id);
    }

    public String namespace() {
        return settings.namespace();
    }

    public String serverId() {
        return settings.server();
    }

    public void playerJoined(Player player) {
        if (player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player) arrivals.put(player.getUniqueId(), player);
    }

    public void playerQuit(Player player) {
        arrivals.remove(player.getUniqueId(), player);
        transfers.remove(player.getUniqueId());
        Waiting current = waiting.get(player.getUniqueId());
        if (current != null && current.player == player) drop(current, true);
    }

    public void close() {
        closed = true;
        ticker.cancel();
        hosted.values().forEach(match -> match.reservation.release());
        hosted.clear();
        localMatches.forEach(match -> match.reservation.release());
        localMatches.clear();
    }

    private void tick() {
        expireLocalState();
        startLocalMatches();
        for (Waiting entry : List.copyOf(waiting.values())) {
            if (!eligible(entry.player, entry.ranked)) drop(entry, true);
        }
        if (exchanging) return;
        long now = System.nanoTime();
        publicationFences.values().removeIf(fence -> fence <= now);
        changes.removeIf(change -> "cancel".equals(change.action()) && !publicationFences.containsKey(change.player()));
        if (!redisAvailable) fallback();

        // Probe with no publications while degraded so repeated failures cannot renew a fence.
        List<Entry> entries = redisAvailable ? waiting.values().stream().map(this::snapshot).toList() : List.of();
        List<Change> batch = List.copyOf(changes);
        Map<Change, SaveBeforeTransfer> saves = new HashMap<>();
        List<Change> valid = new ArrayList<>();
        for (Change change : batch) {
            if (("vote".equals(change.action()) || "depart".equals(change.action()))) {
                Waiting entry = waiting.get(change.player());
                if (entry == null || !entry.token.equals(change.token())) continue;
                if ("depart".equals(change.action()) && !hosted.containsKey(change.match())) {
                    ExtendedPlayer profile = plugin.getExtendedPlayer(entry.player);
                    saves.put(change, new SaveBeforeTransfer(profile.getStat(), profile.getMainPlayer()));
                }
            }
            valid.add(change);
        }
        changes.clear();

        MatchmakingPolicy.Host heartbeat = settings.hostMatches() ? hostSnapshot() : null;
        StatManagement.RedisWrapper connection = StatManagement.getRedis();
        exchanging = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Exchange response;
            try {
                response = exchange(connection, heartbeat, entries, valid, saves);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[Matchmaking] Queue exchange failed.", error);
                response = new Exchange(true, System.nanoTime(), null);
            }
            Exchange result = response;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> accept(result, entries, valid));
        });
    }

    private Exchange exchange(StatManagement.RedisWrapper connection, MatchmakingPolicy.Host heartbeat,
                              List<Entry> entries, List<Change> batch, Map<Change, SaveBeforeTransfer> saves) {
        boolean[] mayHavePublished = {false};
        List<Change> ready = new ArrayList<>();
        for (Change action : batch) {
            SaveBeforeTransfer data = saves.get(action);
            if (data == null || StatManagement.flushForTransfer(data.stat, data.profile)) ready.add(action);
        }
        long sentAt = System.nanoTime();
        List<Assignment> matches = connection.withJedis(client -> {
            mayHavePublished[0] = true;
            return redis.exchange(client, session, heartbeat, entries, ready);
        });
        if (matches != null) {
            connection.withJedis(client -> {
                redis.findMatches(client, session);
                return true;
            });
        }
        return new Exchange(mayHavePublished[0], sentAt, matches);
    }

    private void accept(Exchange response, List<Entry> published, List<Change> attempted) {
        if (closed) return;
        exchanging = false;
        if (response.mayHavePublished) {
            long fence = deadline(RedisMatchmaking.FALLBACK_FENCE_MS);
            for (Entry entry : published) {
                // Leave/rejoin and queue switches must observe the old ticket's lease too.
                publicationFences.put(entry.ticket().player(), fence);
            }
        }

        boolean available = response.assignments != null;
        if (available != redisAvailable) {
            redisAvailable = available;
            plugin.getLogger().info(available ? "[Matchmaking] Redis recovered. Network queues resumed."
                    : "[Matchmaking] Redis unavailable. Local queues will resume as network assignments expire.");
        }
        if (!available) {
            for (Change change : attempted) {
                if (switch (change.action()) { case "cancel", "abort", "finish" -> true; default -> false; }) {
                    changes.add(change);
                }
            }
            return;
        }

        Set<String> current = new HashSet<>();
        for (Assignment match : response.assignments) {
            long expires = response.sentAt + TimeUnit.MILLISECONDS.toNanos(match.remaining());
            if (expires <= System.nanoTime()) continue;
            current.add(match.id());
            if (isHost(match)) handleHostOffer(match, expires);
            handleSource(match, match.first(), match.departedFirst(), expires);
            handleSource(match, match.second(), match.departedSecond(), expires);
            if (isHost(match)) handleHostStart(match);
        }
        for (Hosted hold : List.copyOf(hosted.values())) {
            if (!current.contains(hold.match.id())) discard(hold);
        }
    }

    private void handleHostOffer(Assignment match, long expires) {
        if (hosted.containsKey(match.id()) || !"offered".equals(match.state())) return;
        if (expires < deadline(RedisMatchmaking.MIN_READY_MS) || hosted.size() >= settings.pendingLimit()
                || reservedFor(match.first().player()) || reservedFor(match.second().player())) {
            changes.add(Change.host("abort", match.id()));
            return;
        }

        int incoming = incoming(match);
        if (incoming > Math.max(0, Bukkit.getMaxPlayers() - Bukkit.getOnlinePlayers().size() - pendingArrivals())) {
            changes.add(Change.host("abort", match.id()));
            return;
        }
        var reservation = host.reserve(match.kit(), match.first().ranked(), match.firstKit(), match.secondKit());
        if (reservation == null) {
            changes.add(Change.host("abort", match.id()));
            return;
        }
        hosted.put(match.id(), new Hosted(match, reservation, expires));
    }

    private void handleSource(Assignment match, MatchmakingPolicy.Ticket ticket, boolean departed, long expires) {
        if (!settings.server().equals(ticket.server())) return;
        Waiting local = waiting.get(ticket.player());
        if (local == null || !local.token.equals(ticket.token())) return;
        if (!eligible(local.player, local.ranked)) {
            drop(local, true);
            return;
        }
        if ("reserved".equals(match.state())) {
            changes.add(Change.source("vote", match.id(), ticket));
        } else if ("ready".equals(match.state())) {
            if (departed && expires > deadline(1_000)) {
                drop(local, false);
                transfers.put(ticket.player(), new Transfer(match.id(), expires));
                if (isHost(match)) {
                    local.player.sendMessage("§aMatch found. Waiting for your opponent to arrive.");
                } else {
                    local.player.sendMessage("§aMatch found. Connecting to §f" + match.host().server() + "§a.");
                    if (!ProxyTransfer.connect(plugin, local.player, match.host().server())) {
                        changes.add(Change.source("cancel", match.id(), ticket));
                        transfers.remove(ticket.player());
                    }
                }
            } else if (!departed) {
                changes.add(Change.source("depart", match.id(), ticket));
            }
        }
    }

    private void handleHostStart(Assignment match) {
        Hosted hold = hosted.get(match.id());
        if (hold == null) return;
        if ("offered".equals(match.state())) {
            if (hold.expires < deadline(RedisMatchmaking.MIN_READY_MS)
                    || (!hold.reservation.pending() && !hold.reservation.ready())) {
                changes.add(Change.host("abort", match.id()));
                discard(hold);
            } else if (hold.reservation.ready()) {
                changes.add(Change.host("reserve", match.id()));
            }
            return;
        }
        if (!"ready".equals(match.state()) && !"started".equals(match.state())) return;
        Player first = arrived(match.first());
        Player second = arrived(match.second());
        if (first == null || second == null) {
            if ("started".equals(match.state())) {
                changes.add(Change.host("finish", match.id()));
                discard(hold);
            }
            return;
        }
        if ("ready".equals(match.state())) {
            if (match.departedFirst() && match.departedSecond()) changes.add(Change.host("start", match.id()));
            return;
        }

        hosted.remove(match.id());
        transfers.remove(match.first().player());
        transfers.remove(match.second().player());
        changes.add(Change.host("finish", match.id()));
        if (plugin.getConfig().getBoolean("network.matchmaking.return_after_match", true)) {
            hold.reservation.duel().returnPlayersTo(Map.of(
                    first.getUniqueId(), match.first().server(), second.getUniqueId(), match.second().server()));
        }
        launch(hold.reservation, first, second, match.first().ranked());
    }

    private Player arrived(MatchmakingPolicy.Ticket ticket) {
        Player player = Bukkit.getPlayer(ticket.player());
        if (!eligible(player, ticket.ranked())) return null;
        return settings.server().equals(ticket.server()) || arrivals.get(ticket.player()) == player ? player : null;
    }

    private void fallback() {
        long now = System.currentTimeMillis();
        List<MatchmakingPolicy.Ticket> candidates = new ArrayList<>(waiting.values().stream()
                .filter(entry -> !publicationFences.containsKey(entry.player.getUniqueId())
                        && !reservedFor(entry.player.getUniqueId()))
                .map(entry -> snapshot(entry).ticket())
                .sorted(Comparator.comparingLong(MatchmakingPolicy.Ticket::since)).toList());

        int launched = 0;
        for (var first : List.copyOf(candidates)) {
            if (!waiting.containsKey(first.player())) continue;
            var match = policy.best(first, candidates, List.of(hostSnapshot()), now, true);
            if (match == null) continue;
            Waiting a = waiting.get(match.first().player());
            Waiting b = waiting.get(match.second().player());
            var reservation = host.reserve(match.kit(), a.ranked, a.custom, b.custom);
            if (reservation == null) continue;

            drop(a, true);
            drop(b, true);
            candidates.remove(match.first());
            candidates.remove(match.second());
            localMatches.add(new LocalMatch(a, b, reservation, deadline(RedisMatchmaking.MATCH_LEASE_MS)));
            if (++launched >= settings.offersPerPoll()) break;
        }
        startLocalMatches();
    }

    private void startLocalMatches() {
        for (LocalMatch match : List.copyOf(localMatches)) {
            boolean eligible = eligible(match.first.player, match.first.ranked)
                    && eligible(match.second.player, match.second.ranked);
            if (eligible && match.expires > System.nanoTime() && match.reservation.pending()) continue;

            localMatches.remove(match);
            if (eligible && match.expires > System.nanoTime() && match.reservation.ready()) {
                launch(match.reservation, match.first.player, match.second.player, match.first.ranked);
            } else {
                match.reservation.release();
                notifyTimeout(match.first.player.getUniqueId());
                notifyTimeout(match.second.player.getUniqueId());
            }
        }
    }

    private void launch(QueueDuelHost.Reservation reservation, Player first, Player second, boolean ranked) {
        try {
            first.sendMessage("§aMatch found against §f" + second.getName() + "§a.");
            second.sendMessage("§aMatch found against §f" + first.getName() + "§a.");
            reservation.start(first, second, ranked);
        } catch (RuntimeException failure) {
            for (Player player : List.of(first, second)) {
                ExtendedPlayer profile = plugin.getExtendedPlayer(player);
                var current = profile.getPlayerDuelInstance();
                if (current != null && current.getDuelInstance() == reservation.duel()) {
                    profile.setPlayerDuelInstance(null);
                    plugin.teleportToLobby(player);
                }
                notifyTimeout(player.getUniqueId());
            }
            reservation.release();
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Matchmaking] Could not start queued duel.", failure);
        }
    }

    private Entry snapshot(Waiting local) {
        UUID id = local.player.getUniqueId();
        ExtendedPlayer player = plugin.getExtendedPlayer(local.player);
        int elo = local.ranked ? player.getMainPlayer().getKitRankedStats(local.kit).getElo() : 0;
        var ticket = new MatchmakingPolicy.Ticket(id, local.token, settings.server(), settings.pool(), local.kit,
                local.ranked, local.since, Math.clamp(local.player.getPing(), 0, 3000), elo, local.custom != null);
        return new Entry(ticket, local.custom);
    }

    private void drop(Waiting local, boolean cancel) {
        UUID id = local.player.getUniqueId();
        waiting.remove(id);
        if (cancel) changes.add(new Change("cancel", null, id, local.token));
        if (local.ranked) DuelManager.getRankedQueueModule().removeQueueState(id);
        else DuelManager.getDuelQueueModule().removeQueueState(id);
    }

    private MatchmakingPolicy.Host hostSnapshot() {
        return host.snapshot(settings.server(), session, settings.pool(), pendingArrivals());
    }

    private int pendingArrivals() {
        return hosted.values().stream().mapToInt(hold -> incoming(hold.match)).sum();
    }

    private int incoming(Assignment match) {
        return (Bukkit.getPlayer(match.first().player()) == null ? 1 : 0)
                + (Bukkit.getPlayer(match.second().player()) == null ? 1 : 0);
    }

    private boolean reservedFor(UUID id) {
        return hosted.values().stream().anyMatch(hold -> isParticipant(hold.match, id))
                || localMatches.stream().anyMatch(match -> match.first.player.getUniqueId().equals(id)
                || match.second.player.getUniqueId().equals(id));
    }

    private boolean isHost(Assignment match) {
        return settings.server().equals(match.host().server()) && session.equals(match.host().session());
    }

    private static boolean isParticipant(Assignment match, UUID id) {
        return match.first().player().equals(id) || match.second().player().equals(id);
    }

    private void expireLocalState() {
        long now = System.nanoTime();
        for (Hosted hold : List.copyOf(hosted.values())) {
            if (hold.expires <= now) discard(hold);
        }
        for (var active : List.copyOf(transfers.entrySet())) {
            if (active.getValue().expires <= now) expireTransfer(active.getKey(), active.getValue());
        }
    }

    private void discard(Hosted hold) {
        hosted.remove(hold.match.id());
        hold.reservation.release();
        for (UUID id : List.of(hold.match.first().player(), hold.match.second().player())) {
            Transfer active = transfers.get(id);
            if (active != null && active.match.equals(hold.match.id())) expireTransfer(id, active);
            else if (arrivals.containsKey(id) && !waiting.containsKey(id)) notifyTimeout(id);
        }
    }

    private void expireTransfer(UUID id, Transfer transfer) {
        if (transfers.remove(id, transfer)) notifyTimeout(id);
    }

    private void notifyTimeout(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) player.sendMessage("§eYour queued match could not start. You can join the queue again.");
    }

    public static boolean eligible(Player player, boolean ranked) {
        if (player == null || !player.isOnline()) return false;
        Core plugin = Core.getInstance();
        ExtendedPlayer data = plugin.getExtendedPlayer(player);
        if (data == null || !data.isReady() || data.getPlayerDuelInstance() != null
                || data.isSpectating() || data.isLavaRisingEvent()) return false;
        String lobby = plugin.getConfig().getString("lobby_world");
        if (lobby != null && !lobby.equalsIgnoreCase(player.getWorld().getName())) return false;
        TournamentManager tournaments = TournamentManager.getInstance();
        if (tournaments != null && tournaments.isInTournament(player)) return false;
        return !ranked || plugin.getPartyManager() == null || !plugin.getPartyManager().isInParty(player);
    }

    public static int eloRange(int seconds) {
        return MatchmakingPolicy.eloRange(seconds);
    }

    private CustomKit selectedKit(Player player) {
        ExtendedPlayer data = plugin.getExtendedPlayer(player);
        int slot = data.getSelectedKit().getOrDefault(player.getUniqueId(), 1);
        CustomKit[] kits = data.getMainPlayer().getKits();
        return kits != null && slot >= 0 && slot < kits.length ? kits[slot] : null;
    }

    private static long deadline(long milliseconds) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
    }
}
