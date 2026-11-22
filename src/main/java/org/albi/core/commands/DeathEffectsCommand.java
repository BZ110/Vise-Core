package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeathEffectsCommand implements CommandExecutor {

    private final Core plugin;

    public DeathEffectsCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "messages.death-effects-command.only-players-can-use-this-command");
            return true;
        }

        DeathEffectsGUI gui = plugin.getDeathEffectsGUI();
        if (gui != null) {
            gui.open(player, 0);
        } else {
            Messages.send(player, "messages.death-effects-command.death-effects-gui-is-not-available");
        }

        return true;
    }
}
