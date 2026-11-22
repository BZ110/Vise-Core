package org.albi.core.modules.CustomKit.kcommands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.albi.core.modules.CustomKit.commands.BaseCommand;

public class KitCommand extends BaseCommand {

    private final Core plugin;
    private final int kit;

    public KitCommand(Core plugin, int kit) {
        this.plugin = plugin; this.kit = kit;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null) return true;

        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return true;

        int kitIndex = kit; // for /k1

        CustomKit[] kits = mp.getKits();
        if (kits.length <= kitIndex || kits[kitIndex] == null) {
            plugin.getMessageAPI().sendMessage(player, "&cKit " + kitIndex + " does not exist.");
            return true;
        }

        CustomKit kit = kits[kitIndex];
        plugin.resetPlayer(player, false);
        plugin.applyPossibleKit(player, kit.toKit());
        plugin.getMessageAPI().sendMessage(player, "&aLoaded Kit " + kitIndex);
        return true;
    }
}
