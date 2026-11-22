package org.albi.core.events;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.albi.core.Core;

public final class OnServerLoaded implements Listener {

    private final Core plugin;

    public OnServerLoaded(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        String lobbyWorldName = plugin.getConfig().getString("lobby_world", "world"); // default to "world" if missing

        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(lobbyWorldName)) {
                world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
                plugin.getLogger().info("[Core] Applied custom gamerules to lobby world.");
                continue;
            }

            world.setDifficulty(Difficulty.HARD);
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
            world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
            plugin.getLogger()
                    .info("[Core] Set difficulty of \"" + world.getName() + "\" and applied basic gamerules.");
        }
    }
}
