package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class OnWorldChangeListener implements Listener {

    private final Core plugin;

    public OnWorldChangeListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();

        String toName = to.getName();

        // Ensure duel worlds always have keep inventory & immediate respawn
        if (toName.endsWith("-duel")) {
            to.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
            to.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
            to.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            to.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            to.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            to.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
        }

        // A Polar round can move to a different world within the same duel.
        var profile = plugin.getExtendedPlayer(player);
        var fighter = profile.getPlayerDuelInstance();
        if (from.getName().endsWith("-duel") && fighter != null && fighter.getStatus() != null) {
            var duel = fighter.getDuelInstance();
            if (duel != null && !to.equals(duel.getActiveWorld())) duel.leave(player);
        }

        var spectators = DuelManager.getSpectators();
        if (spectators != null) spectators.worldChanged(player, to);
    }

    @EventHandler
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            // If you only want to block cross-world pearl teleports:
            if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                event.setCancelled(true);
            }
        }
    }

}
