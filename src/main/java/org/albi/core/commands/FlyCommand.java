package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyCommand extends BaseCommand {

    private final Core plugin;

    public FlyCommand(Core plugin) {
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

        if(!player.hasPermission(plugin.getConfig().getString("permissions.fly"))) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);

        player.setAllowFlight(!ep.isFly());
        ep.setFly(!ep.isFly());

        if(ep.isFly())
            plugin.getMessageAPI().sendMessageLang(player, "fly_yes");
        else plugin.getMessageAPI().sendMessageLang(player, "fly_no");

        return true;

    }
}
