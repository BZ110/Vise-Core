package org.albi.core.modules.Duels.spectating;

import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.data.SqlNetworkState;
import org.albi.core.util.ConfigValues;
import org.albi.core.util.ProxyTransfer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.sql.SQLException;

import static org.albi.core.modules.Duels.spectating.SpectateDirectory.Intent;
import static org.albi.core.modules.Duels.spectating.SpectateDirectory.Request;
import static org.albi.core.modules.Duels.spectating.SpectateDirectory.Route;

final class NetworkSpectating {
    private static final class Outgoing {
        final Player player;
        final Intent intent;
        final long expires = deadline(SpectateDirectory.REQUEST_MS);
        boolean opened;
        boolean departing;
        boolean sent;

        Outgoing(Player player, String target) {
            this.player = player;
            intent = new Intent(player.getUniqueId(), UUID.randomUUID().toString(), target);
        }
    }

    private record Query(Intent intent, boolean opened) {}
    record Reply(Intent intent, Request request, String error) {}
    private record Decision(Request request, String state) {}
    private record Cancellation(Intent intent, long expires) {}
    private record Round(List<Reply> replies, List<Request> offers) {}
    private record Return(Player player, long expires) {}

    private final Core plugin;
    private final DuelSpectators spectators;
    private final String server;
    private final SpectateDirectory directory;
    private final SqlSpectateDirectory sqlDirectory;
    private volatile boolean sqlPrepared;
    private long nextSqlWarning;
    private final Map<UUID, Outgoing> outgoing = new LinkedHashMap<>();
    private final Map<UUID, Player> arriving = new HashMap<>();
    private final Map<UUID, Return> returning = new HashMap<>();
    private final Map<UUID, Decision> decisions = new LinkedHashMap<>();
    private final List<Cancellation> cancellations = new ArrayList<>();
    private Map<String, Route> published = Map.of();
    private boolean polling;

    NetworkSpectating(Core plugin, DuelSpectators spectators, String namespace, String server) {
        this.plugin = plugin;
        this.spectators = spectators;
        this.server = server;
        directory = new SpectateDirectory(namespace, server);
        sqlDirectory = SqlNetworkState.enabled(plugin.getConfig(), "spectating")
                ? new SqlSpectateDirectory(new SqlNetworkState(StatManagement.getSql()), namespace, server) : null;
        int ticks = sqlDirectory == null ? 10 : ConfigValues.integer(plugin.getConfig(), "network.mysql_failover.poll_ticks", 20, 10, 40);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, ticks);
    }

    void request(Player player, String target) {
        outgoing.put(player.getUniqueId(), new Outgoing(player, target));
    }

    boolean isTransitioning(Player player) {
        UUID id = player.getUniqueId();
        return outgoing.containsKey(id) || arriving.containsKey(id) || returning.containsKey(id);
    }

    boolean cancelTravel(Player player) {
        UUID viewer = player.getUniqueId();
        Outgoing travel = outgoing.get(viewer);
        if ((travel != null && travel.sent) || returning.containsKey(viewer)) {
            spectators.message(player, "duels_transfer_busy");
            return true;
        }
        if (travel != null) remove(travel);
        arriving.remove(viewer, player);
        return false;
    }

    void playerQuit(Player player) {
        UUID viewer = player.getUniqueId();
        Outgoing fromHere = outgoing.remove(viewer);
        if (fromHere != null && !fromHere.sent) cancel(fromHere);
        arriving.remove(viewer, player);
        returning.remove(viewer);
    }

    private void tick() {
        long now = System.nanoTime();
        for (Outgoing travel : List.copyOf(outgoing.values())) {
            if (travel.expires <= now || (!travel.sent && !spectators.canTravel(travel.player))) {
                remove(travel);
                if (online(travel.player)) spectators.message(travel.player, "duels_spectate_unavailable");
            }
        }
        returning.values().removeIf(transfer -> {
            if (transfer.expires > now) return false;
            if (online(transfer.player)) spectators.message(transfer.player, "duels_return_unavailable");
            return true;
        });
        if (polling) return;

        Map<String, Route> routes = spectators.routes(server);
        List<Route> gone = published.values().stream().filter(route -> !route.equals(routes.get(route.name()))).toList();
        List<Query> queries = outgoing.values().stream().filter(travel -> !travel.sent && !travel.departing)
                .map(travel -> new Query(travel.intent, travel.opened)).toList();
        List<Decision> ready = List.copyOf(decisions.values());
        List<Cancellation> cancel = cancellations.stream().filter(item -> item.expires > now).toList();
        decisions.clear();
        cancellations.clear();

        polling = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Round result = exchange(wire -> {
                cancel.forEach(item -> wire.cancel(item.intent));
                gone.forEach(wire::forget);
                wire.publish(routes);
                ready.forEach(decision -> wire.decide(decision.request, decision.state));

                List<Reply> replies = new ArrayList<>();
                for (Query query : queries) {
                    if (query.opened) {
                        Request found = wire.find(query.intent.viewer());
                        if (found == null || !found.token().equals(query.intent.token())) {
                            replies.add(new Reply(query.intent, null, "unavailable"));
                        } else {
                            replies.add(new Reply(query.intent, found, null));
                        }
                    } else {
                        replies.add(wire.open(query.intent));
                    }
                }
                return new Round(replies, wire.pending());
            });
            onMain(() -> {
                polling = false;
                if (result == null) {
                    cancellations.addAll(cancel);
                    if (sqlDirectory != null) {
                        ready.forEach(decision -> decisions.putIfAbsent(decision.request.viewer(), decision));
                        return; // Bounded by the original request deadline. Never switch authorities.
                    }
                    for (Query query : queries) {
                        if (!query.opened) deliver(new Reply(query.intent, null, "unavailable"));
                    }
                    return;
                }
                published = routes;
                result.replies.forEach(this::deliver);
                result.offers.forEach(this::consider);
            });
        });
    }

    private void consider(Request request) {
        Player target = Bukkit.getPlayer(request.target().player());
        var result = spectators.inspect(target, request.target().duel(), request.viewer());
        String state = switch (result) {
            case OK -> "ready";
            case BLOCKED -> "blocked";
            default -> "ended";
        };
        decisions.put(request.viewer(), new Decision(request, state));
    }

    private void deliver(Reply reply) {
        Outgoing travel = outgoing.get(reply.intent.viewer());
        if (travel == null || !travel.intent.equals(reply.intent) || !online(travel.player)) return;
        if (reply.error != null || !spectators.canTravel(travel.player)) {
            remove(travel);
            String key = switch (reply.error == null ? "" : reply.error) {
                case "missing" -> "duels_duel_not_found";
                case "busy" -> "duels_transfer_busy";
                default -> "duels_spectate_unavailable";
            };
            spectators.message(travel.player, key);
            return;
        }

        travel.opened = true;
        switch (reply.request.state()) {
            case "ready" -> depart(travel, reply.request);
            case "blocked", "ended" -> {
                remove(travel);
                spectators.message(travel.player, "blocked".equals(reply.request.state())
                        ? "duels_spectate_blocked" : "duels_duel_already_over");
            }
            default -> { }
        }
    }

    private void depart(Outgoing travel, Request request) {
        if (travel.departing) return;
        travel.departing = true;
        var profile = plugin.getExtendedPlayer(travel.player);
        var stat = profile.getStat();
        var main = profile.getMainPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean sent = StatManagement.flushForTransfer(stat, main)
                    && Boolean.TRUE.equals(exchange(wire -> wire.depart(request)));
            onMain(() -> {
                if (outgoing.get(travel.intent.viewer()) != travel || !online(travel.player)) return;
                if (sent && travel.expires > deadline(1000) && spectators.canTravel(travel.player)) {
                    travel.sent = true;
                    spectators.message(travel.player, "duels_spectate_connecting");
                    if (ProxyTransfer.connect(plugin, travel.player, request.target().server())) return;
                }
                remove(travel);
                spectators.message(travel.player, "duels_spectate_unavailable");
            });
        });
    }

    void arrive(Player player) {
        if (!online(player)) return;
        UUID viewer = player.getUniqueId();
        arriving.put(viewer, player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Request request = take(viewer);
            onMain(() -> {
                if (arriving.remove(viewer, player) && request != null && online(player)) {
                    spectators.watchOnArrival(player, request);
                }
            });
        });
    }

    private Request take(UUID viewer) {
        // A connection can arrive during another SQL transaction. Retry contention only.
        int attempts = sqlDirectory == null ? 1 : 3;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                if (sqlDirectory == null) return exchange(wire -> wire.take(viewer));
                if (!StatManagement.isSqlReady()) return null;
                prepareSql();
                return sqlDirectory.exchange((wire, now) -> wire.take(viewer));
            } catch (SQLException failure) {
                if (!SqlNetworkState.busy(failure)) { warn(failure); return null; }
                try { Thread.sleep(30L * (attempt + 1)); }
                catch (InterruptedException stop) { Thread.currentThread().interrupt(); return null; }
            }
        }
        return null;
    }

    interface Wire {
        void publish(Map<String, Route> routes);
        void forget(Route route);
        Reply open(Intent intent);
        Request find(UUID viewer);
        List<Request> pending();
        void decide(Request request, String state);
        boolean depart(Request request);
        Request take(UUID viewer);
        void cancel(Intent intent);
    }

    private <T> T exchange(Function<Wire, T> callback) {
        if (sqlDirectory != null) {
            if (!StatManagement.isSqlReady()) return null;
            try {
                prepareSql();
                return sqlDirectory.exchange((wire, now) -> callback.apply(wire));
            } catch (SQLException failure) {
                if (!SqlNetworkState.busy(failure)) warn(failure);
                return null;
            }
        }
        return StatManagement.getRedis().withJedis(redis -> callback.apply(new Wire() {
            public void publish(Map<String, Route> routes) { directory.publish(redis, routes); }
            public void forget(Route route) { directory.forget(redis, route); }
            public Reply open(Intent intent) {
                String result = directory.open(redis, intent);
                return "missing".equals(result) || "busy".equals(result) ? new Reply(intent, null, result)
                        : new Reply(intent, SpectateDirectory.parse(result), null);
            }
            public Request find(UUID viewer) { return directory.find(redis, viewer); }
            public List<Request> pending() { return directory.pending(redis); }
            public void decide(Request request, String state) { directory.decide(redis, request, state); }
            public boolean depart(Request request) { return directory.depart(redis, request); }
            public Request take(UUID viewer) { return directory.take(redis, viewer); }
            public void cancel(Intent intent) { directory.cancel(redis, intent); }
        }));
    }

    private synchronized void prepareSql() throws SQLException {
        if (!sqlPrepared) {
            sqlDirectory.prepare();
            sqlPrepared = true;
        }
    }

    private synchronized void warn(SQLException failure) {
        if (System.nanoTime() < nextSqlWarning && nextSqlWarning != 0) return;
        nextSqlWarning = deadline(30_000);
        if ("42S02".equals(failure.getSQLState())) sqlPrepared = false;
        plugin.getLogger().warning("[Help Me Please] MySQL spectating is temporarily unavailable (" + failure.getSQLState() + ").");
    }

    void returnHome(Player player, String origin, CompletableFuture<Boolean> lobby) {
        if (server.equals(origin) || !online(player)) return;
        var transfer = new Return(player, deadline(10_000));
        returning.put(player.getUniqueId(), transfer);

        var profile = plugin.getExtendedPlayer(player);
        var main = profile.getMainPlayer();
        var stat = profile.getStat();
        lobby.whenComplete((ok, error) -> onMain(() -> {
            if (!canReturn(transfer)) return;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean saved = main != null && stat != null && StatManagement.flushForTransfer(stat, main);
                onMain(() -> {
                    if (!canReturn(transfer)) return;
                    if (saved && ProxyTransfer.connect(plugin, player, origin)) return;
                    returning.remove(player.getUniqueId(), transfer);
                    spectators.message(player, "duels_return_unavailable");
                });
            });
        }));
    }

    private boolean canReturn(Return transfer) {
        Player player = transfer.player;
        if (!online(player) || returning.get(player.getUniqueId()) != transfer || transfer.expires <= System.nanoTime()) {
            return false;
        }
        var profile = plugin.getExtendedPlayer(player);
        return profile.getPlayerDuelInstance() == null && !profile.isSpectating();
    }

    private void remove(Outgoing travel) {
        outgoing.remove(travel.intent.viewer(), travel);
        cancel(travel);
    }

    private void cancel(Outgoing travel) {
        cancellations.add(new Cancellation(travel.intent, travel.expires));
    }

    private void onMain(Runnable callback) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, callback);
    }

    private static boolean online(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private static long deadline(long milliseconds) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
    }
}
