package org.albi.core.modules.CustomKit.commands;

import org.albi.core.Core;
import org.albi.core.modules.CustomKit.helpers.CustomKitCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class CategoryListCommand extends BaseCommand {

    private final Core plugin;

    public CategoryListCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
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

        Map<String, CustomKitCategory> categories =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");

        if (categories == null || categories.isEmpty()) {
            player.sendMessage("§cNo categories have been created yet.");
            return true;
        }

        player.sendMessage("§6Custom Kit Categories:");
        for (Map.Entry<String, CustomKitCategory> entry : categories.entrySet()) {
            CustomKitCategory cat = entry.getValue();
            player.sendMessage(" §e• §a" + entry.getKey() + " §7(Slot: " + cat.getSlot() + ")");
        }

        return true;
    }
}
