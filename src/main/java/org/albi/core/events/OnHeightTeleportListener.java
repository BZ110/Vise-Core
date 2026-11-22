package org.albi.core.events;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class OnHeightTeleportListener implements Listener {

    private final Core plugin;

    public OnHeightTeleportListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Make sure the player actually moved vertically
        if (event.getFrom().getY() == event.getTo().getY()) return;


        int maxHeight = plugin.getConfig().getInt("lobby_max_height", 150);

        if (plugin.getLobbySettings().protection().heightLimit() && plugin.isLobby(player)
                && player.getLocation().getY() > maxHeight) {
            plugin.teleportToLobby(player);
        }
    }
}
