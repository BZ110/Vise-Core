package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpahereCommand extends BaseCommand {
    private final Core plugin;
    public TpahereCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length != 1) return error(p, "Usage: /tpahere <player>");

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || target == p) return error(p, "Invalid player.");

        plugin.teleportRequests.put(target.getUniqueId(), new TeleportRequest(p.getUniqueId(), 30_000, TeleportRequest.Type.TPA_HERE));
        plugin.getMessageAPI().sendMessage(p, "&aSent a summon request to &f" + target.getName());
        plugin.getMessageAPI().sendMessage(target, "&e" + p.getName() + " wants you to teleport to them. Type &a/tpaccept " + p.getName());

        return true;
    }

    private boolean error(Player p, String msg) {
        plugin.getMessageAPI().sendMessage(p, "&c" + msg);
        return true;
    }
}
