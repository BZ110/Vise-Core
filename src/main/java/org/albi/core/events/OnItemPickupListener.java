package org.albi.core.events;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;

import java.util.Objects;

public class OnItemPickupListener implements Listener {

    private final Core plugin;

    public OnItemPickupListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();

        if (plugin.getConfig().getString("lobby_world")
                .equals(player.getWorld().getName())) {

            if (!player.hasPermission(Objects.requireNonNull(plugin.getConfig().getString("permissions.bypass")))
                    && !plugin.getExtendedPlayer(player).isBypassRegion()) {
                event.setCancelled(true);
            }
        }
    }
}
