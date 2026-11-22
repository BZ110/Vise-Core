package org.albi.core.modules.Duels.commands;

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
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /rankedadmin list <kit>");
                return true;
            }
            String kitName = args[1];
            List<String> arenas = DuelManager.stor.getRankedArenas().get(kitName);
            if (arenas == null || arenas.isEmpty()) {
                sender.sendMessage("§cNo ranked arenas configured for §f" + kitName + "§c.");
            } else {
                sender.sendMessage("§6Ranked Arenas for §f" + kitName + "§6: §7" + String.join(", ", arenas));
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
            sender.sendMessage("§cKit '" + kitName + "' not found.");
            return true;
        }
        if (arena == null) {
            sender.sendMessage("§cArena '" + arenaName + "' not found.");
            return true;
        }

        List<String> list = DuelManager.stor.getRankedArenas().computeIfAbsent(kit.getName(), k -> new ArrayList<>());

        if (sub.equals("add")) {
            if (list.contains(arena.getName())) {
                sender.sendMessage("§cArena already in ranked pool for this kit.");
            } else {
                list.add(arena.getName());
                sender.sendMessage("§aAdded arena §f" + arena.getName() + " §ato ranked pool for §f" + kit.getName() + "§a.");
            }
        } else if (sub.equals("remove")) {
            if (list.remove(arena.getName())) {
                sender.sendMessage("§aRemoved arena §f" + arena.getName() + " §afrom ranked pool for §f" + kit.getName() + "§a.");
            } else {
                sender.sendMessage("§cArena not in ranked pool for this kit.");
            }
        } else {
            sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6§lRanked Admin Commands:");
        sender.sendMessage("§e/rankedadmin add <kit> <arena>");
        sender.sendMessage("§e/rankedadmin remove <kit> <arena>");
        sender.sendMessage("§e/rankedadmin list <kit>");
    }
}
