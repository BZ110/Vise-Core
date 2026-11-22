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

        var protection = plugin.getLobbySettings().protection();
        boolean lobby = plugin.isLobby(player);
        var material = event.getBlockPlaced().getType();
        if (!lobby && protection.blockPlayerHeadsOutsideLobby()
                && (material == org.bukkit.Material.PLAYER_HEAD || material == org.bukkit.Material.PLAYER_WALL_HEAD)) {
            event.setCancelled(true);
            return;
        }

        if (lobby && protection.blockPlace()) {
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
