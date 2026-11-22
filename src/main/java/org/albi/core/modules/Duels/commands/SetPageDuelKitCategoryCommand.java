package org.albi.core.modules.Duels.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.Duels.helpers.DuelKitPageCategory;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SetPageDuelKitCategoryCommand extends BaseCommand {
    private final Core plugin;
    public SetPageDuelKitCategoryCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "messages.set-page-duel-kit-category-command.players-only"); return true; }
        if (!player.hasPermission("core.dueladmin")) { Messages.send(player, "messages.set-page-duel-kit-category-command.no-permission"); return true; }
        if (args.length != 2) { Messages.send(player, "messages.set-page-duel-kit-category-command.usage-setpageduelkitcategory-page-gui-slot"); return true; }

        int page, guiSlot;
        try {
            page = Integer.parseInt(args[0]);
            guiSlot = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            Messages.send(player, "messages.set-page-duel-kit-category-command.page-and-gui-slot-must-both-be-numbers");
            return true;
        }

        if (page < 0) { Messages.send(player, "messages.set-page-duel-kit-category-command.page-must-be-0-or-greater"); return true; }
        if (guiSlot < 0 || guiSlot > 26) { Messages.send(player, "messages.set-page-duel-kit-category-command.gui-slot-must-be-between-0-and-26"); return true; }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { Messages.send(player, "messages.set-page-duel-kit-category-command.hold-the-category-display-item-in-your-main"); return true; }

        Map<Integer, DuelKitPageCategory> categories =
                (Map<Integer, DuelKitPageCategory>) plugin.getData().get("duelKitPageCategories");
        if (categories == null) categories = new HashMap<>();

        categories.put(guiSlot, new DuelKitPageCategory(page, guiSlot, new SerializableItem(hand)));
        plugin.getData().set("duelKitPageCategories", categories);

        Messages.send(player, "messages.set-page-duel-kit-category-command.saved-duel-kit-category-for-page-at-gui", page, guiSlot);
        return true;
    }
}
