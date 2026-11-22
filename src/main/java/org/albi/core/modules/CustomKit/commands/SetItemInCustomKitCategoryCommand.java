package org.albi.core.modules.CustomKit.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.CustomKit.helpers.CustomKitCategory;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class SetItemInCustomKitCategoryCommand extends BaseCommand {

    private final Core plugin;
    public SetItemInCustomKitCategoryCommand(Core plugin) { this.plugin = plugin; }

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
        if (args.length < 2) {
            Messages.send(player, "messages.set-item-in-custom-kit-category-command.usage-setitemincustomkitcategory-name-gui-slot");
            return true;
        }

        String name = args[0].toLowerCase();
        int guiSlot;
        try { guiSlot = Integer.parseInt(args[1]); }
        catch (NumberFormatException ex) {
            Messages.send(player, "messages.set-item-in-custom-kit-category-command.slot-must-be-a-number-0-53");
            return true;
        }
        if (guiSlot < 0 || guiSlot > 53) {
            Messages.send(player, "messages.set-item-in-custom-kit-category-command.slot-must-be-between-0-and-53");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            Messages.send(player, "messages.set-item-in-custom-kit-category-command.hold-the-item-you-want-to-store");
            return true;
        }

        Map<String, CustomKitCategory> categories =
                (Map<String, CustomKitCategory>) plugin.getData().get("customKitCategories");
        if (categories == null || !categories.containsKey(name)) {
            Messages.send(player, "messages.set-item-in-custom-kit-category-command.no-category-named", name);
            return true;
        }

        CustomKitCategory cat = categories.get(name);
        cat.setItem(guiSlot, new SerializableItem(hand));
        plugin.getData().set("customKitCategories", categories);

        Messages.send(player, "messages.set-item-in-custom-kit-category-command.item-stored-in-category-at-gui-slot", name, guiSlot);
        return true;
    }
}
