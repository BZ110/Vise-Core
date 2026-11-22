package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.serializable.SerializableSpawnInventory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class MsgCommand extends BaseCommand {

    private final Core plugin;

    public MsgCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        // ─────────────────────────────────────────────
        //  Player & permission checks
        // ─────────────────────────────────────────────
        if (!(sender instanceof Player player)) {
            plugin.getMessageAPI().sendMessageLang(sender, "players_only");
            return true;
        }

        String cmdName = command.getName().toLowerCase();
        int secondsForCooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        if(eP.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            eP.addCooldown(cmdName, secondsForCooldown);
        }

        if (!player.hasPermission("core.msg")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        // ─────────────────────────────────────────────
        //  Usage: /msg <player> <message…>
        // ─────────────────────────────────────────────
        if (args.length < 2) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_usage");
            return true;
        }

        /* ───────────────────────  parse target  ─────────────────────────── */
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_offline");
            return true;
        }

        /* ────────  BLOCK LIST CHECK  ──────── */
        UUID senderId = player.getUniqueId();
        UUID targetId = target.getUniqueId();

        org.albi.core.modules.Friends.FriendsManager friends = org.albi.core.modules.ModuleManager.getModule(org.albi.core.modules.Friends.FriendsManager.class);
        boolean friendBypass = friends != null && friends.isFriends(player, target);

        if (plugin.getExtendedPlayer(target)
                .getMainPlayer()
                .getBlockedList()
                .contains(senderId) || (!friendBypass && !plugin.getExtendedPlayer(target).getMainPlayer().allowMessages())) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_blocked_by_target");
            return true;
        }
        if (plugin.getExtendedPlayer(player)
                .getMainPlayer()
                .getBlockedList()
                .contains(targetId)) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_you_blocked_target");
            return true;
        }

        /* ───────────────────────  normal messaging flow  ────────────────── */
        // set up /r reply target
        plugin.getExtendedPlayer(target).setReplyTarget(player);

        if (player.getName().equals(target.getName())) {
            plugin.getMessageAPI().sendMessageLang(player, "msg_you");
            return true;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        plugin.getMessageAPI().sendMessageLang(
                player, "msg_to",   new String[]{player.getName(), target.getName(), message});
        plugin.getMessageAPI().sendMessageLang(
                target, "msg_from", new String[]{player.getName(), target.getName(), message});

        return true;
    }
}
