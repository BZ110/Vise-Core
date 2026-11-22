package org.albi.core.modules.FFA.managers;

import org.albi.core.Core;
import org.albi.core.modules.FFA.FFAManager;
import org.albi.core.modules.FFA.commands.FFAAdminCommand;
import org.albi.core.modules.FFA.commands.FFACommand;
import org.albi.core.modules.FFA.commands.FFAKitEditorCommand;
import org.bukkit.command.CommandExecutor;

public final class CommandManager {
    public CommandManager(Core plugin, FFAManager mgr) {
        register(plugin, "ffa", new FFACommand(plugin, mgr));
        register(plugin, "ffaadmin", new FFAAdminCommand(plugin, mgr));
        register(plugin, "ffakiteditor", new FFAKitEditorCommand(plugin));
    }

    private void register(Core plugin, String name, CommandExecutor exec) {
        try {
            plugin.getLogger().info("[FFA] Registering Command: /" + name);
            plugin.getCommand(name).setExecutor(exec);
        } catch (Exception e) {
            plugin.getLogger().severe("[FFA] Could not register /" + name + ": " + e.getMessage());
        }
    }
}