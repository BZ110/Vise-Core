package org.albi.core.modules.Duels.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.albi.core.modules.Duels.helpers.Arena;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /spectate <player>
 *
 * Teleports a player into SPECTATOR mode at the arena of the target player,
 * provided the target is currently in an active duel.
 */
public class SpectateCommand extends BaseCommand {

    private final Core plugin;

    public SpectateCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!(sender instanceof Player))
            return false;
        Player player = (Player) sender;

        /* cooldown check (defaults to 3 s if not specified in config) */
        String cmdName = command.getName().toLowerCase();
        int cdSeconds = plugin.getConfig().getInt("cooldowns." + cmdName, 3);
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep.checkCooldown(cmdName)) {
            plugin.getMessageAPI().sendMessageLang(player, "cooldown");
            return true;
        }
        ep.addCooldown(cmdName, cdSeconds);

        /* ── basic arg validation ─────────────────────────────────────── */
        if (args.length < 1) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_duel_specify");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_duel_not_found");
            return true;
        }

        /* ── cannot spectate while you’re in a duel yourself ──────────── */
        if (ep.getPlayerDuelInstance() != null) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_already_in_duel");
            return true;
        }

        /* ── verify the target is actually in an active duel ──────────── */
        ExtendedPlayer targetEP = plugin.getExtendedPlayer(target);
        PlayerDuelInstance pdi = targetEP.getPlayerDuelInstance();
        if (pdi == null || pdi.getDuelInstance() == null) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_not_in_duel");
            return true;
        }

        DuelInstance duel = pdi.getDuelInstance();
        if (duel.isCanceled()) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_duel_already_over");
            return true;
        }

        /* ── teleport spectator to the arena centre ───────────────────── */
        Location baseX;
        if (pdi.getTeam() == 1)
            baseX = duel.getTeamLocOne();
        else
            baseX = duel.getTeamLocTwo();

        Location base = duel.getMotherLocation();

        if (base == null) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_invalid_arena");
            return true;
        }

        if (plugin.getExtendedPlayer(target).getMainPlayer().getBlockedList().contains(player.getUniqueId())
                || !plugin.getExtendedPlayer(target).getMainPlayer().allowSpectators()) {
            plugin.getMessageAPI().sendMessageLang(player, "duels_spectate_blocked");
            return true;
        }

        /* ── tracking spectator state in ExtendedPlayer ──────────────────── */
        ep.setSpectating(true);
        ep.setSpectatingArena(duel.getArena());
        ep.setSpectatingBase(base);
        ep.teleportBase = baseX;

        player.teleportAsync(baseX.clone().add(0, 15, 0));

        /* ── optionally cache in DuelInstance list ────────────────────── */
        duel.addSpectator(player);

        plugin.getMessageAPI().sendMessageLang(
                player,
                "duels_now_spectating",
                new String[] { target.getName() });

        player.setGameMode(GameMode.SPECTATOR);
        return true;
    }
}
