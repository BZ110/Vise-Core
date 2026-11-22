package org.albi.core.modules.FFA.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.FFA.FFAManager;
import org.albi.core.modules.FFA.GUIs.FFAGUIs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FFACommand extends org.albi.core.commands.BaseCommand {

    private final Core plugin;
    private final FFAGUIs guis;

    public FFACommand(Core plugin, FFAManager mgr) {
        this.plugin = plugin;
        this.guis = new FFAGUIs(plugin);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        org.albi.core.data.ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        
        if (org.albi.core.modules.Tournaments.TournamentManager.getInstance() != null && org.albi.core.modules.Tournaments.TournamentManager.getInstance().isInTournament(p)) {
            Messages.send(p, "messages.ffacommand.you-cannot-join-an-ffa-while-you-are");
            return true;
        }

        if (ep == null || !ep.isReady()) return true;
        guis.openKitMenu(p);
        return true;
    }
}