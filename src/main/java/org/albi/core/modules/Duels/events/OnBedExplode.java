package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class OnBedExplode implements Listener {

    private final Core plugin;
    public OnBedExplode(Core p){ this.plugin = p; }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerInteractEvent e) {

        /* trigger only when a player right-clicks an existing bed */
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Material clicked = e.getClickedBlock().getType();
        if (!clicked.name().endsWith("_BED")) return;   // white_bed, red_bed, etc.

        Player p = e.getPlayer();

        boolean listedWorld = plugin.getConfig()
                .getStringList("c_worlds")
                .stream()
                .anyMatch(w -> w.equalsIgnoreCase(p.getWorld().getName()));

        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        boolean inGame = ep != null
                && ep.getPlayerDuelInstance() != null
                && "InGame".equalsIgnoreCase(ep.getPlayerDuelInstance().getStatus());

        if (inGame || listedWorld) {
            e.setCancelled(true);               // stop the sleep animation

            // remove both halves of the bed so no item drops
            e.getClickedBlock().setType(Material.AIR, false);

            p.getWorld().createExplosion(
                    e.getClickedBlock().getLocation(),
                    5F,        // power
                    true,     // set fire
                    true      // break blocks
                    );        // do not credit any player
        }
    }
}
