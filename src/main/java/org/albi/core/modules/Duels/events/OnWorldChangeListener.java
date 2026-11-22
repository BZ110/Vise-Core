package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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

    private boolean isPolarWorld(String name) {
        return name.startsWith("p.") && name.contains("-p-") && name.endsWith("-duel");
    }

    private String extractArenaName(String worldName) {
        // P.sumo-p-3-duel → sumo

        if (!worldName.toLowerCase().startsWith("p."))
            return worldName;

        int start = 2; // skip "P."
        int end = worldName.indexOf("-p-");

        if (end == -1)
            return worldName;

        return worldName.substring(start, end);
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();

        String fromName = from.getName();
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

        // 1) Existing duel-cleanup (source world) — FIXED
        if (fromName.endsWith("-duel")) {

            boolean fromPolar = isPolarWorld(fromName);
            boolean toPolar = isPolarWorld(toName);

            // ✅ Allow ONLY: polar → polar SAME arena
            if (fromPolar && toPolar) {
                String fromArena = extractArenaName(fromName);
                String toArena = extractArenaName(toName);

                if (fromArena.equalsIgnoreCase(toArena)) {
                    return; // SAME polar arena → do NOT leave duel
                }
            }

            // ❌ Everything else → leave duel
            ExtendedPlayer ep = plugin.getExtendedPlayer(player);

            if (ep != null && ep.getPlayerDuelInstance() != null
                    && ep.getPlayerDuelInstance().getStatus() != null) {
                ep.getPlayerDuelInstance().getDuelInstance().leave(player);
            }
        }

        // 2) Arrival-based inventory wipe (unchanged)
        String toWorld = toName;

        /*
         * if (plugin.getConfig().getStringList("c_worlds").contains(toWorld)) {
         * player.getInventory().clear();
         * player.getEnderChest().clear();
         * }
         */

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);

        if (ep != null && ep.isSpectating()) {
            if (!toWorld.endsWith("-duel")) {
                ep.clearSpectatorStatus();
            } else {
                player.setGameMode(GameMode.SPECTATOR);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                }, 2L);
            }
        }
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
