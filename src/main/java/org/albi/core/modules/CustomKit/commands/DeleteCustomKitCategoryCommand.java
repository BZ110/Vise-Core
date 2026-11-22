package org.albi.core.modules.CustomKit.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.CustomKit.helpers.CustomKitCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class DeleteCustomKitCategoryCommand extends BaseCommand {

    private final Core plugin;
    public DeleteCustomKitCategoryCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command cmd,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }
        if (!player.hasPermission("core.customkit.admin")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }
        if (args.length < 1) {
            Messages.send(player, "messages.delete-custom-kit-category-command.usage-deletecustomkitcategory-name");
            return true;
        }

        Map<String, CustomKitCategory> categories =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (categories == null || !categories.containsKey(args[0].toLowerCase())) {
            Messages.send(player, "messages.delete-custom-kit-category-command.no-category-named", args[0]);
            return true;
        }

        categories.remove(args[0].toLowerCase());
        plugin.getData().set("customKitCategories", categories);

        Messages.send(player, "messages.delete-custom-kit-category-command.category-deleted", args[0]);
        return true;
    }
}
