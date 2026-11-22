package org.albi.core.events;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.CompletableFuture;

public class OnJoinListener implements Listener {

    private final Core plugin;

    public OnJoinListener(Core plugin) {
        this.plugin = plugin;
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        var settings = plugin.getLobbySettings().join();
        if (settings.hideJoinMessage()) event.setJoinMessage(null);

        plugin.getCache().removeExtendedPlayer(player);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (settings.resetPlayer()) plugin.resetPlayer(player);
        var lobbyArrival = settings.teleport() ? plugin.teleportToLobbyAsync(player)
                : CompletableFuture.completedFuture(plugin.isLobby(player));

        if (settings.warmTierProfiles()) plugin.getTierManager().fetchAll(player.getUniqueId(), true);

        ep.whenReady().whenComplete((ignored, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
                if (error != null) {
                    Messages.send(player, "messages.on-join-listener.profile-unavailable");
                    return;
                }
                Messages.send(player, "messages.on-join-listener.your-profile-is-now-ready");

                plugin.equipLobbyElytra(player);
            });
        });
        lobbyArrival.thenCombine(ep.whenReady(), (arrived, ignored) -> arrived).thenAccept(arrived -> {
            if (!arrived || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                var matchmaking = DuelManager.getMatchmaking();
                if (matchmaking != null) matchmaking.playerJoined(player);
                var spectators = DuelManager.getSpectators();
                if (spectators != null) spectators.playerJoined(player);
            });
        });
    }


}
