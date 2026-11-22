package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LobbyPlayersCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;
    private final String lobbyWorld;

    public LobbyPlayersCommand(Core plugin) {
        this.plugin = plugin;
        this.lobbyWorld = plugin.getConfig().getString("lobby_world");
    }

    /* ───────────── command ───────────── */
    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "messages.lobby-players-command.only-players-may-run-this-command");
            return true;
        }

        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(lobbyWorld)) {
            plugin.getMessageAPI().sendMessageLang(player, "lobbyplayers_not_in_lobby");
            return true;
        }

        // toggling logic
        boolean turnOff = args.length > 0 && args[0].equalsIgnoreCase("off");
        boolean turnOn  = args.length > 0 && args[0].equalsIgnoreCase("on");

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);

        if (args.length == 0) { // no argument → toggle
            turnOff = !ep.isHideLobbyPlayers();
            turnOn  = !turnOff;
        }

        if (turnOff) {
            // hide everyone
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != player) player.hidePlayer(plugin, p);
            }
            ep.setHideLobbyPlayers(true);
            plugin.getMessageAPI().sendMessageLang(player, "lobbyplayers_off");
        } else if (turnOn) {
            // show everyone
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != player) player.showPlayer(plugin, p);
            }
            ep.setHideLobbyPlayers(false);
            plugin.getMessageAPI().sendMessageLang(player, "lobbyplayers_on");
        } else {
            plugin.getMessageAPI().sendMessageLang(player, "lobbyplayers_usage");
        }
        return true;
    }

    /* ───────────── tab‑complete ───────────── */
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        if (a.length == 1) return Arrays.asList("on", "off");
        return Collections.emptyList();
    }
}
