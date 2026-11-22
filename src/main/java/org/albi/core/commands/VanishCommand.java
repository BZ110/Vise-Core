package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;

    public VanishCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        String cmdName = cmd.getName().toLowerCase();
        int secondsForCooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        if(eP.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            eP.addCooldown(cmdName, secondsForCooldown);
        }

        if (!player.hasPermission("core.vanish")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        var ep = plugin.getExtendedPlayer(player);
        boolean isVanished = ep.isVanish();
        ep.setVanish(!isVanished);

        if (!isVanished) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission("core.vanish.see")) {
                    other.hidePlayer(plugin, player);
                }
            }
            plugin.getMessageAPI().sendMessageLang(player, "vanish_enabled");
        } else {
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, player);
            }
            plugin.getMessageAPI().sendMessageLang(player, "vanish_disabled");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("core.vanish.see")) {
            return Collections.emptyList();
        }

        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !plugin.getExtendedPlayer(p).isVanish())
                .map(Player::getName)
                .collect(Collectors.toList());
    }
}
