package org.albi.core.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.modules.Duels.DuelInstance;
import org.albi.core.modules.Duels.helpers.PlayerDuelInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class OnEntityMax implements Listener {

    private final Core plugin;

    public OnEntityMax(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpawn(PlayerInteractEvent e) {

        if(e.getItem() == null) return;
        if(e.getMaterial() != Material.CREEPER_SPAWN_EGG) return;
        if(e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if(e.getClickedBlock() == null) return;
        e.setCancelled(true);
        e.getItem().setAmount(e.getItem().getAmount()-1);

        ExtendedPlayer ep = plugin.getExtendedPlayer(e.getPlayer());
        PlayerDuelInstance duelInstance = ep.getPlayerDuelInstance();

        if(duelInstance != null)
            if (duelInstance.getStatus().equalsIgnoreCase("Countdown"))
                return;

        Location loc;
        if(e.getClickedBlock().isSolid()) {
            loc = e.getClickedBlock().getLocation().add(e.getBlockFace().getDirection()).add(0.5,0,0.5);
        } else {
            loc = e.getClickedBlock().getLocation().add(0.5, 0, 0.5);
        }

        Creeper c = e.getPlayer().getWorld().spawn(loc, Creeper.class);
        c.setIgnited(true);
        c.setMaxFuseTicks(20);
    }
}
