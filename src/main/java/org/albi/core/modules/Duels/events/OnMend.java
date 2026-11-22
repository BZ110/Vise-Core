package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemMendEvent;

public class OnMend implements Listener {

    private final Core plugin;

    public OnMend(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerItemMend(PlayerItemMendEvent event) {

        Player player = event.getPlayer();

        /* ───────────────────────────── */
        /* Duel + kit safety chain      */
        /* ───────────────────────────── */
        ExtendedPlayer ep = plugin.getExtendedPlayer(player);
        if (ep == null) return;

        PlayerDuelInstance duel = ep.getPlayerDuelInstance();
        if (duel == null) return;

        /* ───────────────────────────── */
        /* Metadata lookup (typed)      */
        /* ───────────────────────────── */
        Integer mendingRNG =
                duel.getKit()
                        .getMetaData()
                        .getInt("mending_rng");

        if (mendingRNG == null || mendingRNG <= 0) return;

        /* ───────────────────────────── */
        /* Apply repair override        */
        /* ───────────────────────────── */
        event.setRepairAmount(mendingRNG);
    }
}
