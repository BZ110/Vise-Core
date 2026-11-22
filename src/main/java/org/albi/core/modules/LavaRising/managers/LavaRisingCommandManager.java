package org.albi.core.modules.LavaRising.managers;

import org.albi.core.Core;
import org.albi.core.modules.LavaRising.commands.*;
import org.bukkit.command.CommandExecutor;

public class LavaRisingCommandManager {


    public LavaRisingCommandManager(Core plugin) {
        registerCommand(plugin, "lavasetspawn", new LavaSetSpawnCommand(plugin));
        registerCommand(plugin, "lavaarena", new LavaArenaCommand(plugin));
        registerCommand(plugin, "lavaspawn", new LavaSpawnCommand(plugin));
        registerCommand(plugin, "lavaforcestart", new LavaForceStartCommand(plugin));
    }

    private void registerCommand(Core plugin, String commandName, CommandExecutor executor) {
        try {
            plugin.getLogger().info("[LavaRising] Registering Command: /" + commandName);
            plugin.getCommand(commandName).setExecutor(executor);
        } catch (Exception e) {
            plugin.getLogger().severe("[LavaRising] Could not register command: /" + commandName);
        }
    }
}
