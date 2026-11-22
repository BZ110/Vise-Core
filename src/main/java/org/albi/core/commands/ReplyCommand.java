package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ReplyCommand extends BaseCommand {

    private final Core plugin;
    public ReplyCommand(Core plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {

        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }
        if (!player.hasPermission("core.msg")) {            // reuse same perm as /msg
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessageAPI().sendMessageLang(player, "r_usage");
            return true;
        }

        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        Player target = ep.getReplyTarget();

        if (target == null || !target.isOnline()) {
            plugin.getMessageAPI().sendMessageLang(player, "r_no_target");
            return true;
        }

        /* ───────  block checks  ─────── */
        UUID senderId = player.getUniqueId();
        UUID targetId = target.getUniqueId();
        
        org.albi.core.modules.Friends.FriendsManager friends = org.albi.core.modules.ModuleManager.getModule(org.albi.core.modules.Friends.FriendsManager.class);
        boolean friendBypass = friends != null && friends.isFriends(player, target);
        
        if (plugin.getExtendedPlayer(target).getMainPlayer().getBlockedList().contains(senderId)  || (!friendBypass && !plugin.getExtendedPlayer(target).getMainPlayer().allowMessages())) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_blocked_by_target");
            return true;
        }
        if (ep.getMainPlayer().getBlockedList().contains(targetId)) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_you_blocked_target");
            return true;
        }

        String message = String.join(" ", args);

        plugin.getMessageAPI().sendMessageLang(
                player, "msg_to",   new String[]{player.getName(), target.getName(), message});
        plugin.getMessageAPI().sendMessageLang(
                target, "msg_from", new String[]{player.getName(), target.getName(), message});

        plugin.getExtendedPlayer(target).setReplyTarget(player); // refresh chain
        return true;
    }
}
