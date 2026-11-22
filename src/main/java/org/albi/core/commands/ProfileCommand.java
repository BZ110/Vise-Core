package org.albi.core.commands;

import org.albi.core.Core;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ProfileCommand extends BaseCommand {

    private final Core plugin;

    public ProfileCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(plugin.getMessageAPI().toColorLang("players_only"));
            return true;
        }

        // Determine target
        OfflinePlayer target = viewer;
        if (args.length > 0) {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || !target.hasPlayedBefore()) {
                plugin.getMessageAPI().sendMessage(viewer, "&cThat player has never played on this server.");
                return true;
            }
        }

        OfflinePlayer finalTarget = target;
        
        // Privacy check
        org.albi.core.data.MainPlayer targetMp = org.albi.core.data.StatManagement.getMain(finalTarget.getUniqueId());
        if (targetMp != null && !targetMp.visibleStats() && !viewer.getUniqueId().equals(finalTarget.getUniqueId())) {
            if (!viewer.hasPermission("core.profile.bypass")) {
                plugin.getMessageAPI().sendMessage(viewer, "&cThat player's profile is currently private.");
                return true;
            }
        }

        // Open GUI - ProfileGUI handles its own async fetching
        new ProfileGUI(plugin).open(viewer, finalTarget);

        return true;
    }
}
