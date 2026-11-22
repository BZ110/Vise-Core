package org.albi.core.modules.CustomKit.managers;

import org.albi.core.Core;
import org.albi.core.modules.CustomKit.commands.*;
import org.albi.core.modules.CustomKit.kcommands.*;
import org.bukkit.command.CommandExecutor;

public class CustomKitCommandManager {
    public CustomKitCommandManager(Core plugin) {
        registerCommand(plugin, "customkit", new CustomKitCommand(plugin));
        registerCommand(plugin, "createcustomkitcategory", new CreateCustomKitCategoryCommand(plugin));
        registerCommand(plugin, "setitemincustomkitcategory", new SetItemInCustomKitCategoryCommand(plugin));
        registerCommand(plugin, "deletecustomkitcategory", new DeleteCustomKitCategoryCommand(plugin));
        registerCommand(plugin, "categorylist", new CategoryListCommand(plugin));
        registerCommand(plugin, "changecategorylogo", new ChangeCategoryLogoCommand(plugin));
        registerCommand(plugin, "copykit", new CopyKitCommand(plugin));

        // K Commands
        for (int i = 1; i <= 36; i++) {
            registerCommand(plugin, "k" + i, new KitCommand(plugin, i));
        }

    }

    private void registerCommand(Core plugin, String commandName, CommandExecutor executor) {
        try {
            plugin.getLogger().info("[CustomKit] Registering Command: /" + commandName);
            plugin.getCommand(commandName).setExecutor(executor);
        } catch (Exception e) {
            plugin.getLogger().severe("[CustomKit] Could not register command: /" + commandName);
        }
    }
}
