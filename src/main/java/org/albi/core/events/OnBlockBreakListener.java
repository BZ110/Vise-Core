package org.albi.core.events;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Objects;

public class OnBlockBreakListener implements Listener {

    private final Core plugin;

    public OnBlockBreakListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Check if we're in the lobby.
        if(plugin.getConfig().getString("lobby_world")
                .equals(player.getWorld().getName())) {

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
