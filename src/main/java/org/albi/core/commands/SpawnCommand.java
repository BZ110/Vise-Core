package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand extends BaseCommand {

    private final Core plugin;

    public SpawnCommand(Core plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return false;

        String cmdName = command.getName().toLowerCase();
        int secondsForCooldown = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer eP = plugin.getExtendedPlayer(player);
        if(eP.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        } else {
            eP.addCooldown(cmdName, secondsForCooldown);
        }


        if(!player.hasPermission("core.spawn")) {
            plugin.getMessageAPI().sendMessageLang(player, "no_permission");
            return true;
        }

        var spectators = DuelManager.getSpectators();
        if (spectators != null && spectators.spawn(player)) return true;
        var matchmaking = DuelManager.getMatchmaking();
        if (matchmaking != null && matchmaking.hasPendingMatch(player)) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_transfer_busy");
            return true;
        }
        var duel = eP.getPlayerDuelInstance();
        if (duel != null && duel.getDuelInstance() != null) {
            duel.getDuelInstance().leave(player);
            return true;
        }

        plugin.resetPlayer(player);
        plugin.teleportToLobby(player);

        return true;
    }
}
