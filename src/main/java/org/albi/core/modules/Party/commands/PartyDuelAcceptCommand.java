package org.albi.core.modules.Party.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.DuelPreference;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.modules.Party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public class PartyDuelAcceptCommand extends BaseCommand {

    private final Core plugin;
    private final PartyManager party;
    public PartyDuelAcceptCommand(Core pl, PartyManager pm){ plugin=pl; party=pm; }

    @Override public boolean onCommand(CommandSender s, Command cmd,
                                       String l, String[] a){

        if(!(s instanceof Player player)){ return true; }

        /* cooldown (unchanged) */
        String n = cmd.getName().toLowerCase();
        int cd = plugin.getConfig().getInt("cooldowns."+n,3);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if(ep.checkCooldown(n)){ plugin.getMessageAPI().sendMessageLang(player,"cooldown");return true; }
        ep.addCooldown(n,cd);

        if(a.length<1){ plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true; }
        Player leaderA = Bukkit.getPlayerExact(a[0]);
        if(leaderA==null){ plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true; }

        ExtendedPlayer eA = plugin.getExtendedPlayer(leaderA);
        if(!eA.hasPref(player.getUniqueId())){
            plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true;
        }

        /* must both be leaders */
        if(!(party.isLeader(player) && party.isLeader(leaderA))){
            plugin.getMessageAPI().sendMessageLang(player,"duel_blocked");
            plugin.getMessageAPI().sendMessageLang(leaderA,"duel_blocked");
            return true;
        }

        DuelPreference pref = eA.prefsFor(player.getUniqueId());
        Kit kit = pref.getKit(); Arena arena = pref.getArena(); int rounds = pref.getRounds();

        var partyA = party.getParty(leaderA);
        var partyB = party.getParty(player);
        if(partyA==null || partyB==null){
            plugin.getMessageAPI().sendMessageLang(player,"party_none");return true;
        }

        Player[] teamOne = partyA.getMembers().stream()
                .map(Bukkit::getPlayer).filter(Objects::nonNull).toArray(Player[]::new);
        Player[] teamTwo = partyB.getMembers().stream()
                .map(Bukkit::getPlayer).filter(Objects::nonNull).toArray(Player[]::new);

        DuelInstance duel = new DuelInstance(plugin);
        duel.prepare(teamOne,teamTwo,arena,kit,kit,rounds);
        duel.initialize();

        eA.clearPrefs(player.getUniqueId());      // clean up
        return true;
    }
}
