package org.albi.core.modules.Duels.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.Duels.AutomatedCombat.Manager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AutomateCombatCommand extends BaseCommand {
    private final Core plugin;

    public AutomateCombatCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }

        Manager manager = Manager.get();
        if (manager == null) {
            Messages.send(player, "messages.automate-combat-command.experimental-npc-practice-is-disabled-on-this-server");
            return true;
        }

        String name = command.getName();
        var profile = plugin.getExtendedPlayer(player);
        if (profile.checkCooldown(name)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        }

        profile.addCooldown(name, plugin.getConfig().getInt("cooldowns." + name, 3));
        manager.openMenu(player, null);
        return true;
    }
}
