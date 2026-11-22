package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpAcceptCommand extends BaseCommand {
    private final Core plugin;
    public TpAcceptCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player target)) return true;
        if (args.length != 1) {
            plugin.getMessageAPI().sendMessage(target, Messages.text(target, "messages.tp-accept-command.usage-tpaccept-player"));
            return true;
        }

        Player requester = Bukkit.getPlayer(args[0]);
        if (requester == null) {
            plugin.getMessageAPI().sendMessage(target, Messages.text(target, "messages.tp-accept-command.that-player-is-not-online"));
            return true;
        }

        TeleportRequest req = plugin.teleportRequests.get(target.getUniqueId());
        if (req == null || !req.requesterId.equals(requester.getUniqueId()) || req.isExpired()) {
            plugin.getMessageAPI().sendMessage(target, Messages.text(target, "messages.tp-accept-command.no-pending-request-from-that-player"));
            return true;
        }

        if (req.type == TeleportRequest.Type.TPA) {
            requester.teleportAsync(target.getLocation());
            plugin.getMessageAPI().sendMessage(requester, Messages.text(requester, "messages.tp-accept-command.teleporting-to", target.getName()));
        } else {
            target.teleportAsync(requester.getLocation());
            plugin.getMessageAPI().sendMessage(target, Messages.text(target, "messages.tp-accept-command.teleporting-to", requester.getName()));
        }

        plugin.teleportRequests.remove(target.getUniqueId());
        return true;
    }
}
