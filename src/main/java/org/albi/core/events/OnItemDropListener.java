package org.albi.core.events;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

public class OnItemDropListener implements Listener {

    private final Core plugin;

    public OnItemDropListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (plugin.getLobbySettings().protection().itemDrop() && plugin.isLobby(player)) {

            if (!plugin.getExtendedPlayer(player).isBypassRegion()) {
                event.setCancelled(true);
            }
        }
    }
}
