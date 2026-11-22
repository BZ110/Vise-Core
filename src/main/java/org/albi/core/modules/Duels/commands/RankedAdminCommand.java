package org.albi.core.modules.Duels.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RankedAdminCommand implements CommandExecutor {

    private final Core plugin;

    public RankedAdminCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("core.admin.ranked")) {
            Messages.send(sender, "messages.ranked-admin-command.no-permission");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            if (args.length < 2) {
                Messages.send(sender, "messages.ranked-admin-command.usage-rankedadmin-list-kit");
                return true;
            }
            String kitName = args[1];
            List<String> arenas = DuelManager.stor.getRankedArenas().get(kitName);
            if (arenas == null || arenas.isEmpty()) {
                Messages.send(sender, "messages.ranked-admin-command.no-ranked-arenas-configured-for", kitName);
            } else {
                Messages.send(sender, "messages.ranked-admin-command.ranked-arenas-for", kitName, String.join(", ", arenas));
            }
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        String kitName = args[1];
        String arenaName = args[2];

        Kit kit = DuelManager.stor.getKitByName(kitName);
        Arena arena = DuelManager.stor.getArenaByName(arenaName);

        if (kit == null) {
            Messages.send(sender, "messages.ranked-admin-command.kit-not-found", kitName);
            return true;
        }
        if (arena == null) {
            Messages.send(sender, "messages.ranked-admin-command.arena-not-found", arenaName);
            return true;
        }

        List<String> list = DuelManager.stor.getRankedArenas().computeIfAbsent(kit.getName(), k -> new ArrayList<>());

        if (sub.equals("add")) {
            if (list.contains(arena.getName())) {
                Messages.send(sender, "messages.ranked-admin-command.arena-already-in-ranked-pool-for-this-kit");
            } else {
                list.add(arena.getName());
                Messages.send(sender, "messages.ranked-admin-command.added-arena-to-ranked-pool-for", arena.getName(), kit.getName());
            }
        } else if (sub.equals("remove")) {
            if (list.remove(arena.getName())) {
                Messages.send(sender, "messages.ranked-admin-command.removed-arena-from-ranked-pool-for", arena.getName(), kit.getName());
            } else {
                Messages.send(sender, "messages.ranked-admin-command.arena-not-in-ranked-pool-for-this-kit");
            }
        } else {
            sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        Messages.send(sender, "messages.ranked-admin-command.ranked-admin-commands");
        Messages.send(sender, "messages.ranked-admin-command.rankedadmin-add-kit-arena");
        Messages.send(sender, "messages.ranked-admin-command.rankedadmin-remove-kit-arena");
        Messages.send(sender, "messages.ranked-admin-command.rankedadmin-list-kit");
    }
}
