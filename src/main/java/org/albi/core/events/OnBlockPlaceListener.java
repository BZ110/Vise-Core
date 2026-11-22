package org.albi.core.events;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;

public class OnBlockPlaceListener implements Listener {

    private final Core plugin;

    public OnBlockPlaceListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        String lobbyWorldStr = plugin.getConfig().getString("lobby_world");
        String currentWorldStr = player.getWorld().getName();
        
        // Prevent player heads from being placed anywhere unless in the lobby world
        if (event.getBlockPlaced().getType() == org.bukkit.Material.PLAYER_HEAD || 
            event.getBlockPlaced().getType() == org.bukkit.Material.PLAYER_WALL_HEAD) {
            if (lobbyWorldStr == null || !lobbyWorldStr.equals(currentWorldStr)) {
                event.setCancelled(true);
                return;
            }
        }

        // Check if we're in the lobby.
        if (lobbyWorldStr != null && lobbyWorldStr.equals(currentWorldStr)) {

            // If they don't have the permission, don't allow anything
            if (!player.hasPermission(Objects.requireNonNull(plugin.getConfig().getString("permissions.bypass")))) {
                event.setCancelled(true);
                return;
            }

            // If they don't have the bypass, don't allow anything
            if(!plugin.getExtendedPlayer(player).isBypassRegion()) {
                event.setCancelled(true);
                return;
            }
        }

    }

}
