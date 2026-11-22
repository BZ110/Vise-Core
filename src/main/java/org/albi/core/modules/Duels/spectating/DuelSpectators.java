package org.albi.core.modules.Duels.spectating;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.albi.core.modules.Duels.queue.global.GlobalMatchmaking;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Applies the same spectator rules for local commands and arrivals from a proxy. */
public final class DuelSpectators {
    enum Result {
        OK(null),
        NOT_IN_DUEL("duels_not_in_duel"),
        FINISHED("duels_duel_already_over"),
        INVALID_ARENA("duels_invalid_arena"),
        BLOCKED("duels_spectate_blocked");

        final String message;

        Result(String message) {
            this.message = message;
        }
    }

    private record Session(DuelInstance duel, UUID target, String origin) {}

    private final Core plugin;
    private final NetworkSpectating network;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public DuelSpectators(Core plugin, GlobalMatchmaking matchmaking) {
        this.plugin = plugin;
        network = matchmaking == null ? null
                : new NetworkSpectating(plugin, this, matchmaking.namespace(), matchmaking.serverId());
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPrivacy, 20L, 20L);
    }

    public void spectate(Player viewer, String name) {
        ExtendedPlayer profile = plugin.getExtendedPlayer(viewer);
        if (profile.getPlayerDuelInstance() != null) {
            message(viewer, "duels_already_in_duel");
            return;
        }
        if (busy(viewer)) {
            message(viewer, "duels_transfer_busy");
            return;
        }

        Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            start(viewer, target, null, null);
        } else if (network != null && name.matches("[a-zA-Z0-9_]{1,16}")) {
            if (!canTravel(viewer)) {
                message(viewer, "duels_spectate_lobby");
                return;
            }
            DuelManager.leaveQueues(viewer);
            network.request(viewer, name);
        } else {
            // Retain the standalone command's partial local name matching.
            target = Bukkit.getPlayer(name);
            if (target == null) message(viewer, "duels_duel_not_found");
            else start(viewer, target, null, null);
        }
    }

    public boolean busy(Player player) {
        var matchmaking = DuelManager.getMatchmaking();
        return (matchmaking != null && matchmaking.hasPendingMatch(player))
                || (network != null && network.isTransitioning(player));
    }

    boolean canTravel(Player player) {
        if (!GlobalMatchmaking.eligible(player, false)) return false;
        var matchmaking = DuelManager.getMatchmaking();
        if (matchmaking != null && matchmaking.hasPendingMatch(player)) return false;
        return plugin.getPartyManager() == null || !plugin.getPartyManager().isInParty(player);
    }

    public boolean isTransitioning(Player player) {
        return network != null && network.isTransitioning(player);
    }

    public boolean spawn(Player player) {
        if (leave(player, true)) return true;
        return network != null && network.cancelTravel(player);
    }

    public void playerJoined(Player player) {
        if (network != null) network.arrive(player);
    }

    public void playerQuit(Player player) {
        leave(player, false);
        if (network != null) network.playerQuit(player);
    }

    public void worldChanged(Player player, World world) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && !world.equals(session.duel.getActiveWorld())) leave(player, false);
    }

    public void closeMatch(DuelInstance duel) {
        for (Player player : List.copyOf(duel.spectators)) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null && session.duel == duel) leave(player, true);
        }
    }

    public void moveToNextRound(DuelInstance duel) {
        for (Player viewer : List.copyOf(duel.spectators)) {
            Session session = sessions.get(viewer.getUniqueId());
            if (session == null || session.duel != duel) continue;
            Player target = Bukkit.getPlayer(session.target);
            Location view = viewpoint(target, duel);
            if (view == null) leave(viewer, true);
            else move(viewer, session, view);
        }
    }

    public void sendToLobby(Player player, String origin) {
        if (!player.isOnline()) return;
        plugin.resetPlayer(player);
        var lobby = plugin.teleportToLobbyAsync(player);
        if (network != null && origin != null) network.returnHome(player, origin, lobby);
    }

    private boolean leave(Player viewer, boolean returnToLobby) {
        Session session = sessions.remove(viewer.getUniqueId());
        if (session == null) return false;

        session.duel.removeSpectator(viewer);
        ExtendedPlayer profile = plugin.getExtendedPlayer(viewer);
        profile.clearSpectatorStatus();
        if (returnToLobby) sendToLobby(viewer, session.origin);
        else if (viewer.isOnline()) viewer.setGameMode(org.bukkit.GameMode.SURVIVAL);
        return true;
    }

    private void checkPrivacy() {
        for (var entry : List.copyOf(sessions.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && !allows(entry.getValue().duel, viewer.getUniqueId())) {
                message(viewer, "duels_spectate_blocked");
                leave(viewer, true);
            }
        }
    }

    private void start(Player viewer, Player target, UUID expectedDuel, String origin) {
        Result result = inspect(target, expectedDuel, viewer.getUniqueId());
        if (result != Result.OK) {
            message(viewer, result.message);
            if (origin != null) sendToLobby(viewer, origin);
            return;
        }

        DuelInstance duel = playerDuel(target).getDuelInstance();
        Session previous = sessions.get(viewer.getUniqueId());
        if (origin == null && previous != null) origin = previous.origin;
        leave(viewer, false);
        DuelManager.leaveQueues(viewer);
        Session session = new Session(duel, target.getUniqueId(), origin);
        sessions.put(viewer.getUniqueId(), session);
        duel.addSpectator(viewer);
        move(viewer, session, viewpoint(target, duel));
        plugin.getMessageAPI().sendMessageLang(viewer, "duels_now_spectating", new String[] { target.getName() });
    }

    private void move(Player viewer, Session session, Location view) {
        ExtendedPlayer profile = plugin.getExtendedPlayer(viewer);
        profile.setSpectating(true);
        profile.setSpectatingArena(session.duel.getArena());
        profile.setSpectatingBase(session.duel.getMotherLocation());
        profile.teleportBase = view;
        Location destination = view.clone().add(0, 15, 0);
        view.getWorld().getChunkAtAsync(view.getBlockX() >> 4, view.getBlockZ() >> 4).whenComplete((chunk, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline() && sessions.get(viewer.getUniqueId()) == session
                        && profile.teleportBase == view && (error != null || !viewer.teleport(destination))) {
                    message(viewer, "duels_invalid_arena");
                    leave(viewer, true);
                }
            });
        });
    }

    private PlayerDuelInstance playerDuel(Player target) {
        if (target == null || !target.isOnline()) return null;
        ExtendedPlayer profile = plugin.getExtendedPlayer(target);
        return profile.isReady() ? profile.getPlayerDuelInstance() : null;
    }

    private Location viewpoint(Player target, DuelInstance duel) {
        PlayerDuelInstance data = playerDuel(target);
        if (data == null || data.getDuelInstance() != duel || duel.getMotherLocation() == null) return null;
        return data.getTeam() == 1 ? duel.getTeamLocOne() : duel.getTeamLocTwo();
    }

    Result inspect(Player target, UUID expectedDuel, UUID viewer) {
        PlayerDuelInstance data = playerDuel(target);
        if (data == null || data.getDuelInstance() == null) return Result.NOT_IN_DUEL;
        DuelInstance duel = data.getDuelInstance();
        if (!duel.isWatchable() || (expectedDuel != null && !expectedDuel.equals(duel.spectateId()))) {
            return Result.FINISHED;
        }
        if (viewpoint(target, duel) == null) return Result.INVALID_ARENA;
        return allows(duel, viewer) ? Result.OK : Result.BLOCKED;
    }

    private boolean allows(DuelInstance duel, UUID viewer) {
        for (Player fighter : duel.getTeamOnePlayers()) {
            if (!allows(fighter, viewer)) return false;
        }
        for (Player fighter : duel.getTeamTwoPlayers()) {
            if (!allows(fighter, viewer)) return false;
        }
        return true;
    }

    private boolean allows(Player fighter, UUID viewer) {
        var profile = plugin.getExtendedPlayer(fighter).getMainPlayer();
        return profile != null && profile.allowSpectators()
                && (viewer == null || !profile.getBlockedList().contains(viewer));
    }

    Map<String, SpectateDirectory.Route> routes(String server) {
        Map<String, SpectateDirectory.Route> routes = new LinkedHashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (inspect(target, null, null) != Result.OK) continue;
            var duel = playerDuel(target).getDuelInstance();
            routes.put(target.getName(), new SpectateDirectory.Route(target.getUniqueId(), target.getName(), duel.spectateId(), server));
        }
        return routes;
    }

    void watchOnArrival(Player viewer, SpectateDirectory.Request request) {
        if (viewer.hasPermission("core.spectate") && canTravel(viewer)) {
            start(viewer, Bukkit.getPlayer(request.target().player()), request.target().duel(), request.origin());
        } else {
            message(viewer, "duels_spectate_lobby");
        }
    }

    void message(Player player, String key) {
        plugin.getMessageAPI().sendMessageLang(player, key);
    }
}
