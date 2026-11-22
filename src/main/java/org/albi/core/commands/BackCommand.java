package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand extends BaseCommand {

    private final Core plugin;

    public BackCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        Location deathLoc = eP.getDeathlocation();

        // No saved death location
        if (deathLoc == null) {
            Messages.send(player, "messages.back-command.you-have-no-location-to-return-to", ChatColor.RED);
            return true;
        }

        // Check if world is in freedom list
        boolean isFreedomWorld = plugin.getConfig()
                .getStringList("c_worlds")
                .stream()
                .anyMatch(name -> name.equalsIgnoreCase(deathLoc.getWorld().getName()));

        if (!isFreedomWorld) {
            Messages.send(player, "messages.back-command.you-cannot-use-back-outside-the-rtp-worlds", ChatColor.RED);
            return true;
        }

        // Teleport and clear the saved location
        player.teleportAsync(deathLoc);
        eP.setDeathLocation(null);
        Messages.send(player, "messages.back-command.teleported-to-your-death-location", ChatColor.GREEN);

        return true;
    }
}
