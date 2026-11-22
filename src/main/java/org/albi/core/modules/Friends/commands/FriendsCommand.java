package org.albi.core.modules.Friends.commands;

import org.albi.core.Core;
import org.albi.core.modules.Friends.FriendsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FriendsCommand implements CommandExecutor {

    private final Core plugin;
    private final FriendsManager manager;

    public FriendsCommand(Core plugin, FriendsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            manager.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUse /friends add <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§cThat player is not online.");
                    return true;
                }
                manager.sendRequest(player, target);
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUse /friends accept <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                manager.accept(player, target);
            }
            case "deny" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUse /friends deny <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                manager.deny(player, target);
            }
            case "remove", "delete" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUse /friends remove <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                manager.removeFriend(player, target);
            }
            default -> plugin.getMessageAPI().toColor(player, "&e/friends &7opens the menu, &e/friends add <player>&7, &e/friends accept <player>&7, &e/friends deny <player>&7, &e/friends remove <player>");
        }
        return true;
    }
}
