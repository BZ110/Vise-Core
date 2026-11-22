package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GMACommand extends BaseCommand {

    private final Core plugin;

    public GMACommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        GameMode targetGamemode = GameMode.ADVENTURE;
        String name = "ADVENTURE";
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

        if (!player.hasPermission(plugin.getConfig().getString("permissions.gma"))) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        if (args.length > 0) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                target.setGameMode(targetGamemode);
                plugin.getMessageAPI().sendMessageLang(player, "gamemode_other", new String[]{target.getName(), name});
            }
            plugin.getMessageAPI().sendMessageLang(player, "gamemode_proper");
        }

        player.setGameMode(targetGamemode);
        plugin.getMessageAPI().sendMessageLang(player, "gamemode", new String[]{name});
        return true;
    }
}
