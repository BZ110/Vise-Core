package org.albi.core.modules.CustomKit.commands;

import org.albi.core.Core;
import org.albi.core.modules.CustomKit.helpers.CustomKitCategory;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ChangeCategoryLogoCommand extends BaseCommand {

    private final Core plugin;

    public ChangeCategoryLogoCommand(Core plugin) {
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
        if (args.length < 1) {
            player.sendMessage("§cUsage: /changecategorylogo <name>");
            return true;
        }

        String name = args[0].toLowerCase();

        Map<String, CustomKitCategory> categories =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (categories == null || !categories.containsKey(name)) {
            player.sendMessage("§cCategory not found: " + name);
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            player.sendMessage("§cHold the new logo item in your main hand");
            return true;
        }

        CustomKitCategory cat = categories.get(name);
        cat.setIcon(new SerializableItem(inHand));
        plugin.getData().set("customKitCategories", categories);

        player.sendMessage("§aCategory §e" + name + "§a logo updated successfully.");
        return true;
    }
}
