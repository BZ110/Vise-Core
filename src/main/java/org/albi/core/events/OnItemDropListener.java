package org.albi.core.events;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.Objects;

public class OnItemDropListener implements Listener {

    private final Core plugin;

    public OnItemDropListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (plugin.getConfig().getString("lobby_world")
                .equals(player.getWorld().getName())) {

            if (!plugin.getExtendedPlayer(player).isBypassRegion()) {
                event.setCancelled(true);
            }
        }
    }
}
