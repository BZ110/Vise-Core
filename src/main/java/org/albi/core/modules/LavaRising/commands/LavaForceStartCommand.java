package org.albi.core.modules.LavaRising.commands;

import org.albi.core.Core;
import org.albi.core.modules.LavaRising.LavaRisingManager;
import org.albi.core.modules.LavaRising.helpers.LavaRisingGameInstance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

public class LavaForceStartCommand extends BaseCommand implements CommandExecutor {

    private final Core plugin;

    public LavaForceStartCommand(Core plugin) {
        this.plugin = plugin;

        // auto‑register
        PluginCommand cmd = plugin.getCommand("lavaforcestart");
        if (cmd != null) cmd.setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command cmd, String label, String[] args) {

        if (!sender.hasPermission("lava.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        // don’t stack matches
        if (LavaRisingManager.playing) {
            sender.sendMessage(ChatColor.YELLOW + "A Lava Rising match is already running.");
            return true;
        }

        LavaRisingGameInstance match = new LavaRisingGameInstance(plugin);
        LavaRisingManager.lavaRisingListener.hook(match);
        match.start();

        LavaRisingManager.playing = true;
        Bukkit.broadcastMessage(ChatColor.GOLD + "» Admin has force‑started a Lava Rising match!");

        return true;
    }
}
