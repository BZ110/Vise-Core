package org.albi.core.modules.Duels.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.Duels.helpers.DuelKitPageCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class DeletePageDuelKitCategoryCommand extends BaseCommand {
    private final Core plugin;
    public DeletePageDuelKitCategoryCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "messages.delete-page-duel-kit-category-command.players-only"); return true; }
        if (!player.hasPermission("core.dueladmin")) { Messages.send(player, "messages.delete-page-duel-kit-category-command.no-permission"); return true; }
        if (args.length != 1) { Messages.send(player, "messages.delete-page-duel-kit-category-command.usage-deletepageduelkitcategory-gui-slot"); return true; }

        int guiSlot;
        try { guiSlot = Integer.parseInt(args[0]); }
        catch (NumberFormatException ex) { Messages.send(player, "messages.delete-page-duel-kit-category-command.gui-slot-must-be-a-number"); return true; }

        if (guiSlot < 0 || guiSlot > 26) { Messages.send(player, "messages.delete-page-duel-kit-category-command.gui-slot-must-be-between-0-and-26"); return true; }

        Map<Integer, DuelKitPageCategory> categories =
                (Map<Integer, DuelKitPageCategory>) plugin.getData().get("duelKitPageCategories");
        if (categories == null || categories.remove(guiSlot) == null) {
            Messages.send(player, "messages.delete-page-duel-kit-category-command.no-duel-kit-category-exists-in-slot", guiSlot);
            return true;
        }
        plugin.getData().set("duelKitPageCategories", categories);
        Messages.send(player, "messages.delete-page-duel-kit-category-command.deleted-duel-kit-category-from-slot", guiSlot);
        return true;
    }
}
