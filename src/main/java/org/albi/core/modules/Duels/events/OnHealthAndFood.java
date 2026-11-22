package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public final class OnHealthAndFood implements Listener {

    private final Core plugin;
    public OnHealthAndFood(Core p){this.plugin=p;}

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e){
        if (!(e.getEntity() instanceof Player p)) return;
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep==null||ep.getPlayerDuelInstance()==null) return;

        var kit = ep.getPlayerDuelInstance().getKit();
        if (!"InGame".equals(ep.getPlayerDuelInstance().getStatus())) return;

        switch (e.getRegainReason()){
            case REGEN -> e.setCancelled(true);                        // disable old‑school regen
            case SATIATED -> { if (!kit.isNaturalRegen()) e.setCancelled(true); }
            default -> {}
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e){
        if (!(e.getEntity() instanceof Player p)) return;
        ExtendedPlayer ep = plugin.getExtendedPlayer(p);
        if (ep==null||ep.getPlayerDuelInstance()==null) return;

        var kit = ep.getPlayerDuelInstance().getKit();
        if (!kit.isLoseHunger()) {
            e.setFoodLevel(20);
            e.setCancelled(true);
        }
    }
}
