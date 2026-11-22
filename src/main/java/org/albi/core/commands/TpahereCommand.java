package org.albi.core.commands;

import org.albi.core.util.ConfigValues;

import org.albi.core.gui.Messages;

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
        if (args.length != 1) return error(p, "messages.tpahere-command.usage");

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || target == p) return error(p, "messages.teleport-request.invalid-player");

        plugin.teleportRequests.put(target.getUniqueId(), new TeleportRequest(p.getUniqueId(), ConfigValues.seconds(plugin.getConfig(), "teleports.requests.expiry_seconds", 30, 1, 3600), TeleportRequest.Type.TPA_HERE));
        Messages.send(p, "messages.tpahere-command.sent-a-summon-request-to", target.getName());
        Messages.send(target, "messages.tpahere-command.wants-you-to-teleport-to-them-type-tpaccept", p.getName(), p.getName());

        return true;
    }

    private boolean error(Player p, String key) {
        Messages.send(p, key);
        return true;
    }
}
