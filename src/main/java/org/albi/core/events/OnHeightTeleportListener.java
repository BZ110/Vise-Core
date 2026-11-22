package org.albi.core.events;

import org.albi.core.Core;
import org.albi.core.serializable.SerializableLocation;
import org.albi.core.serializable.SerializableSpawnInventory;
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


        String lobbyWorld = plugin.getConfig().getString("lobby_world");
        int maxHeight = plugin.getConfig().getInt("lobby_max_height");

        if (player.getWorld().getName().equals(lobbyWorld) && player.getLocation().getY() > maxHeight) {
            plugin.teleportToLobby(player);
        }
    }
}
