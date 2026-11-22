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

public class CreateCustomKitCategoryCommand extends BaseCommand {

    private final Core plugin;
    public CreateCustomKitCategoryCommand(Core plugin) { this.plugin = plugin; }

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
        if (args.length < 2) {
            player.sendMessage("§cUsage: /createcustomkitcategory <name> <guiSlot>");
            return true;
        }

        String name = args[0].toLowerCase();
        int guiSlot;
        try { guiSlot = Integer.parseInt(args[1]); }
        catch (NumberFormatException ex) {
            player.sendMessage("§cSlot must be a number 0‑53");
            return true;
        }
        if (guiSlot < 0 || guiSlot > 53) {
            player.sendMessage("§cSlot must be between 0 and 53");
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            player.sendMessage("§cHold the logo item in your main hand");
            return true;
        }

        Map<String, CustomKitCategory> categories =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (categories == null) categories = new HashMap<>();

        CustomKitCategory cat =
                new CustomKitCategory(name, guiSlot, new SerializableItem(inHand));

        categories.put(name, cat);
        plugin.getData().set("customKitCategories", categories);

        player.sendMessage("§aCategory §e" + name + "§a created at GUI slot " + guiSlot);
        return true;
    }
}
