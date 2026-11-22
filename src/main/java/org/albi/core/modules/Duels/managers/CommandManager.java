package org.albi.core.modules.Duels.managers;

import org.albi.core.Core;
import org.albi.core.modules.Duels.commands.*;
import org.bukkit.command.CommandExecutor;

public class CommandManager {
    public CommandManager(Core plugin) {
        registerCommand(plugin, "dueladmin", new DuelAdminCommand(plugin));
        registerCommand(plugin, "duel", new DuelCommand(plugin));
        registerCommand(plugin, "acceptduel", new AcceptDuelCommand(plugin));
        registerCommand(plugin, "spectate", new SpectateCommand(plugin));
        registerCommand(plugin, "setkitdata", new SetKitDataCommand(plugin));
        registerCommand(plugin, "automatecombat", new AutomateCombatCommand(plugin));
        registerCommand(plugin, "setpageduelkitcategory", new SetPageDuelKitCategoryCommand(plugin));
        registerCommand(plugin, "deletepageduelkitcategory", new DeletePageDuelKitCategoryCommand(plugin));
        registerCommand(plugin, "kiteditor", new KitEditorCommand(plugin));

    }

    private void registerCommand(Core plugin, String commandName, CommandExecutor executor) {
        try {
            plugin.getLogger().info("[Duels] Registering Command: /" + commandName);
            plugin.getCommand(commandName).setExecutor(executor);
        } catch (Exception e) {
            plugin.getLogger().severe("[Duels] Could not register command: /" + commandName);
        }
    }
}
