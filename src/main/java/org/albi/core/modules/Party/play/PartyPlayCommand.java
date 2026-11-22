package org.albi.core.modules.Party.play;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Party.PartyManager;
import org.albi.core.modules.Party.commands.BaseCommand;
import org.albi.core.modules.Party.helpers.Party;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyPlayCommand extends BaseCommand {

    private final Core plugin;
    private final PartyManager party;
    public static final Map<UUID, PartyPlaySession> ACTIVE = new HashMap<>();

    public PartyPlayCommand(Core pl, PartyManager pm) { this.plugin = pl; this.party = pm; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String l, String[] a) {

        if (!(s instanceof Player p)) return true;

        Party partyOfLeader = party.getParty(p);
        if (partyOfLeader == null) {
            plugin.getMessageAPI().sendMessageLang(p, "party_none");
            return true;
        }
        if (!party.isLeader(p)) {
            plugin.getMessageAPI().sendMessageLang(p, "party_not_leader");
            return true;
        }

        // cooldown identical to other party cmds
        String name = cmd.getName().toLowerCase();
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        int cd = plugin.getConfig().getInt("cooldowns." + name, 3);
        if (ep.checkCooldown(name)) { plugin.getMessageAPI().sendMessageLang(p, "cooldown"); return true; }
        ep.addCooldown(name, cd);

        // fresh session
        ACTIVE.put(p.getUniqueId(), new PartyPlaySession());
        new PartyPlayGUIs.SizeSelectorGUI(plugin, party).open(p);
        return true;
    }
}
