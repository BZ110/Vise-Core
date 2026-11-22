package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BlockCommand extends BaseCommand {

    private final Core plugin;
    public BlockCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {

        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }
        if (!player.hasPermission("core.block")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }
        if (args.length != 1) {
            plugin.getMessageAPI().sendMessageLang(player, "block_usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_offline");
            return true;
        }
        if (player.equals(target)) {
            plugin.getMessageAPI().sendMessageLang(player, "block_self");
            return true;
        }

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        UUID tId = target.getUniqueId();

        if (!ep.getMainPlayer().addBlock(tId)) {           // returns false if already present
            plugin.getMessageAPI().sendMessageLang(player, "block_already");
            return true;
        }

        plugin.getMessageAPI().sendMessageLang(player, "block_success", new String[]{
                target.getName()
        });
        return true;
    }
}
