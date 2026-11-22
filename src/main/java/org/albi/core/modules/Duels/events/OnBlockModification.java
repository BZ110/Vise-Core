package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public final class OnBlockModification implements Listener {

    private final Core plugin;

    public OnBlockModification(Core plugin) {
        this.plugin = plugin;
    }

    /* ───────────────────────────── */
    /* Block Place                  */
    /* ───────────────────────────── */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {

        Player p = e.getPlayer();

        if (e.getBlockPlaced().getType() == Material.PLAYER_HEAD &&
                e.getBlockPlaced().getType() == Material.PLAYER_WALL_HEAD) {
            String world = e.getPlayer().getWorld().getName();
            if (!world.equalsIgnoreCase(plugin.getConfig().getString("lobby_world", "lobbytest"))) {
                e.setCancelled(true);
            }
        }


        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        PlayerDuelInstance duel = ep.getPlayerDuelInstance();
        if (duel == null) return;

        String status = duel.getStatus();

        boolean listedWorld = plugin.getConfig()
                .getStringList("c_worlds")
                .stream()
                .anyMatch(w -> w.equalsIgnoreCase(p.getWorld().getName()));

        /* ───────────────────────────── */
        /* Countdown build block        */
        /* ───────────────────────────── */
        if ("Countdown".equalsIgnoreCase(status) && !listedWorld) {
            e.setCancelled(true);
            return;
        }

        /* ───────────────────────────── */
        /* TNT Auto Ignite Metadata     */
        /* ───────────────────────────── */
        if (e.getBlockPlaced().getType() == Material.TNT) {

            Boolean ignite =
                    duel.getKit()
                            .getMetaData()
                            .getBoolean("tnt_ignite");

            // default = true
            if (ignite == null) ignite = true;

            if (!ignite) return; // allow normal placement

            e.setCancelled(true);

            Location spawnLoc = e.getBlockPlaced()
                    .getLocation()
                    .add(0.5, 0.5, 0.5);

            TNTPrimed tnt = p.getWorld()
                    .spawn(spawnLoc, TNTPrimed.class);

            tnt.setFuseTicks(
                    plugin.getConfig().getInt("duels.tnt_seconds") * 20
            );

            /* Remove one TNT from hand */
            ItemStack hand = e.getItemInHand();
            if (hand.getType() == Material.TNT) {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
    }

    /* ───────────────────────────── */
    /* Block Break                  */
    /* ───────────────────────────── */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {

        Player p = e.getPlayer();

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep == null) return;

        PlayerDuelInstance duel = ep.getPlayerDuelInstance();
        if (duel == null) return;

        if (!duel.getKit().isBuild()) {
            e.setCancelled(true);
        }
    }
}
