package org.albi.core.modules.Duels.spectating;

import org.albi.core.Core;
import org.albi.core.data.StatManagement;
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
    private record Reply(Intent intent, Request request, String error) {}
    private record Decision(Request request, String state) {}
    private record Cancellation(Intent intent, long expires) {}
    private record Round(List<Reply> replies, List<Request> offers) {}
    private record Return(Player player, long expires) {}

    private final Core plugin;
    private final DuelSpectators spectators;
    private final String server;
    private final SpectateDirectory directory;
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
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 10);
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
        var connection = StatManagement.getRedis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Round result = connection.withJedis(redis -> {
                cancel.forEach(item -> directory.cancel(redis, item.intent));
                gone.forEach(route -> directory.forget(redis, route));
                directory.publish(redis, routes);
                ready.forEach(decision -> directory.decide(redis, decision.request, decision.state));

                List<Reply> replies = new ArrayList<>();
                for (Query query : queries) {
                    if (query.opened) {
                        Request found = directory.find(redis, query.intent.viewer());
                        if (found == null || !found.token().equals(query.intent.token())) {
                            replies.add(new Reply(query.intent, null, "unavailable"));
                        } else {
                            replies.add(new Reply(query.intent, found, null));
                        }
                    } else {
                        String resultJson = directory.open(redis, query.intent);
                        if ("missing".equals(resultJson) || "busy".equals(resultJson)) {
                            replies.add(new Reply(query.intent, null, resultJson));
                        } else {
                            replies.add(new Reply(query.intent, SpectateDirectory.parse(resultJson), null));
                        }
                    }
                }
                return new Round(replies, directory.pending(redis));
            });
            onMain(() -> {
                polling = false;
                if (result == null) {
                    cancellations.addAll(cancel);
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
        var connection = StatManagement.getRedis();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean sent = StatManagement.flushForTransfer(stat, main)
                    && Boolean.TRUE.equals(connection.withJedis(redis -> directory.depart(redis, request)));
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
        var connection = StatManagement.getRedis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Request request = connection.withJedis(redis -> directory.take(redis, viewer));
            onMain(() -> {
                if (arriving.remove(viewer, player) && request != null && online(player)) {
                    spectators.watchOnArrival(player, request);
                }
            });
        });
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
