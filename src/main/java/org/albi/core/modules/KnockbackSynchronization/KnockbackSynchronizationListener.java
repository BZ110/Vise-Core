package org.albi.core.modules.KnockbackSynchronization;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public final class KnockbackSynchronizationListener implements Listener {

    private final KnockbackSynchronizationService service;

    public KnockbackSynchronizationListener(KnockbackSynchronizationService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity attacker = event.getDamager();
        if (!(victim instanceof Player damaged) || !(attacker instanceof Player hitter)) {
            return;
        }

        service.recordDamage(damaged, hitter);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        service.applyKnockbackSync(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        service.clear(event.getPlayer().getUniqueId());
    }
}
