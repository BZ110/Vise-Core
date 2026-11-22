package org.albi.core.modules.CustomKit.commands;

import org.albi.core.gui.Messages;

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
            Messages.send(player, "messages.create-custom-kit-category-command.usage-createcustomkitcategory-name-gui-slot");
            return true;
        }

        String name = args[0].toLowerCase();
        int guiSlot;
        try { guiSlot = Integer.parseInt(args[1]); }
        catch (NumberFormatException ex) {
            Messages.send(player, "messages.create-custom-kit-category-command.slot-must-be-a-number-0-53");
            return true;
        }
        if (guiSlot < 0 || guiSlot > 53) {
            Messages.send(player, "messages.create-custom-kit-category-command.slot-must-be-between-0-and-53");
            return true;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir()) {
            Messages.send(player, "messages.create-custom-kit-category-command.hold-the-logo-item-in-your-main-hand");
            return true;
        }

        Map<String, CustomKitCategory> categories =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (categories == null) categories = new HashMap<>();

        CustomKitCategory cat =
                new CustomKitCategory(name, guiSlot, new SerializableItem(inHand));

        categories.put(name, cat);
        plugin.getData().set("customKitCategories", categories);

        Messages.send(player, "messages.create-custom-kit-category-command.category-created-at-gui-slot", name, guiSlot);
        return true;
    }
}
