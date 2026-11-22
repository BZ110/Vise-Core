package org.albi.core.modules.CustomKit.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.CustomKit.GUIs.CustomKitGUIMain;
import org.albi.core.modules.Duels.GUIs.DuelMainCommandGUIs;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CustomKitCommand extends BaseCommand {

    private final Core plugin;


    public CustomKitCommand(Core plugin) {
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

        // Check for world.
        World world = player.getWorld();
        World world2 = Bukkit.getWorld(plugin.getConfig().getString("lobby_world", "world"));

        // Other worlds
        World world3 = Bukkit.getWorld("Badlands");
        World world4 = Bukkit.getWorld("Mushroom");
        World world5 = Bukkit.getWorld("Desert");
        World world6 = Bukkit.getWorld("Plains");
        World world7 = Bukkit.getWorld("Snowy");

        if (
                world != world2 &&
                world != world3 &&
                world != world4 &&
                world != world5 &&
                world != world6 &&
                world != world7
        ) {
            plugin.getMessageAPI().sendMessageLang(player, "not_here");
            return true;
        }

        plugin.getGuiKit().displayMain(player);

        return true;
    }
}
