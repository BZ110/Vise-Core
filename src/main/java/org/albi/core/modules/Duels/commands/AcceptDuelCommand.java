package org.albi.core.modules.Duels.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.CustomKit.helpers.CustomKit;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.albi.core.modules.Duels.helpers.DuelPreference;
import org.albi.core.modules.Duels.helpers.Kit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptDuelCommand extends BaseCommand {

    private final Core plugin;
    public AcceptDuelCommand(Core plugin){ this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command cmd,
                                       String lbl, String[] args){

        if(!(sender instanceof Player player)){ return true; }

        /* cooldown (unchanged) */
        String n = cmd.getName().toLowerCase();
        int cd = plugin.getConfig().getInt("cooldowns."+n,3);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if(ep.checkCooldown(n)){ plugin.getMessageAPI().sendMessageLang(player,"cooldown");return true; }
        ep.addCooldown(n,cd);

        if(args.length<1){ plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true; }
        Player challenger = Bukkit.getPlayerExact(args[0]);
        if(challenger==null){ plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true; }
        if (!DuelManager.canStartLocalDuel(plugin, player) || !DuelManager.canStartLocalDuel(plugin, challenger)) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_duel_unable");
            return true;
        }

        ExtendedPlayer ec = plugin.getExtendedPlayer(challenger);

        /* verify the challenger really invited THIS player */
        if(!ec.hasPref(player.getUniqueId())){
            plugin.getMessageAPI().sendMessageLang(player,"duels_duel_specify");return true;
        }

        DuelPreference pref = ec.prefsFor(player.getUniqueId());
        Kit   baseKit = pref.getKit();
        Arena arena   = pref.getArena();
        int   rounds  = pref.getRounds();
        if (baseKit == null || arena == null) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_duel_specify");
            return true;
        }

        /* -------- per-custom-kit resolution (same logic) -------- */
        Kit kitA, kitB;
        if(baseKit!=null && baseKit.isPk()){
            kitA = resolve(challenger, ec, baseKit);
            kitB = resolve(player,     ep, baseKit);
        }else{
            kitA = kitB = baseKit;
        }

        DuelInstance duel = new DuelInstance(plugin);
        DuelManager.leaveQueues(player);
        DuelManager.leaveQueues(challenger);
        duel.prepare(new Player[]{challenger},new Player[]{player},
                arena, kitA, kitB, rounds);
        duel.setTelemetry(pref.isTelemetry());
        duel.initialize(duel.configuredCountdownSeconds(), baseKit!=null && baseKit.isPk());

        ec.clearPrefs(player.getUniqueId());      // clean up
        return true;
    }

    /* helper */
    private Kit resolve(Player p, ExtendedPlayer ex, Kit fallback){
        int slot = ex.getSelectedKit().getOrDefault(p.getUniqueId(),1);
        CustomKit ck = ex.getMainPlayer().getKits()[slot];
        if(ck==null) return fallback;
        Kit k = ck.toKit(); k.setPk(false); return k;
    }
}
