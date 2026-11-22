package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.LangAPI;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BypassCommand extends BaseCommand {

    private final Core plugin;

    public BypassCommand(Core plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Player player = (Player) sender;

        String cmdName = command.getName().toLowerCase();
        int secondsForCooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        if(eP.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            eP.addCooldown(cmdName, secondsForCooldown);
        }

        if(!player.hasPermission(plugin.getConfig().getString("permissions.bypass"))) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);

        if(ep.isBypassRegion()) {
            ep.setBypassRegion(false);
            plugin.getMessageAPI().sendMessageLang(player, "no_region_bypass");
        } else {
            ep.setBypassRegion(true);
            plugin.getMessageAPI().sendMessageLang(player, "yes_region_bypass");
        }

        return true;
    }
}
