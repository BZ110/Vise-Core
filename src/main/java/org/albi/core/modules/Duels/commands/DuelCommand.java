package org.albi.core.modules.Duels.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.GUIs.DuelMainCommandGUIs;
import org.albi.core.modules.Friends.FriendsManager;
import org.albi.core.modules.ModuleManager;
import org.albi.core.modules.Duels.helpers.DuelPreference;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DuelCommand extends BaseCommand {

    private final Core plugin;
    public DuelCommand(Core plugin){ this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command cmd,
                                       String lbl, String[] args){

        if(!(sender instanceof Player player)){ return true; }

        /* cooldown check (unchanged) */
        String name = cmd.getName().toLowerCase();
        int cd = plugin.getConfig().getInt("cooldowns."+name,3);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if(ep.checkCooldown(name)){ plugin.getMessageAPI().sendMessageLang(player,"cooldown");return true; }
        ep.addCooldown(name,cd);

        if(args.length<1){ plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if(target==null){ plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true; }
        if(target==player) { plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true; }
        /* block-list & invite toggle */
        ExtendedPlayer et = plugin.getExtendedPlayer(target);
        FriendsManager friends = ModuleManager.getModule(FriendsManager.class);
        boolean friendBypass = friends != null && friends.isFriends(player, target);
        if(et.getMainPlayer().getBlockedList().contains(player.getUniqueId())
                || (!friendBypass && !et.getMainPlayer().duelInvites())){
            plugin.getMessageAPI().sendMessageLang(player,"duels_duel_unable");return true;
        }

        /* ensure a fresh DuelPreference object exists */
        ep.prefsFor(target.getUniqueId()).setRounds(
                plugin.getConfig().getInt("duels.default_rounds"));   // sensible start

        /* open picker GUI */
        new DuelMainCommandGUIs(plugin).openKitGUI(player, target);
        return true;
    }
}
