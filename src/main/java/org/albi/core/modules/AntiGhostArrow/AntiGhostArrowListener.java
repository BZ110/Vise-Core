package org.albi.core.modules.AntiGhostArrow;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AntiGhostArrowListener implements Listener {

    private final AntiGhostArrowService service;

    public AntiGhostArrowListener(AntiGhostArrowService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof AbstractArrow arrow) {
            service.registerArrow(arrow);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHitEarly(ProjectileHitEvent event) {
        // Prevent processing ghost hits for arrows that vanilla has ALREADY successfully hit an entity with in this tick
        if (event.getHitEntity() != null && event.getEntity() instanceof AbstractArrow arrow) {
            service.markConsumed(arrow.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        // Only immediately unregister for entity hits (already consumed).
        // Block hits MUST stay tracked so the tick task can run a final
        // ghost-detection pass along the arrow's last travel segment.
        if (event.getHitEntity() != null) {
            service.unregisterArrow(arrow);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        service.clearPlayer(event.getPlayer().getUniqueId());
    }
}
