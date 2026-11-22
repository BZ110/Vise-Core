package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.LangAPI;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.serializable.SerializableLocation;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand extends BaseCommand {

    private final Core plugin;

    public SetSpawnCommand(Core plugin) {
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

        if(!player.hasPermission("core.setspawn")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        SerializableLocation serializedSpawn = new SerializableLocation(player.getLocation());
        plugin.getData().set("spawn", serializedSpawn);

        plugin.getMessageAPI().sendMessageLang(player, "spawn_set");

        return true;
    }
}
