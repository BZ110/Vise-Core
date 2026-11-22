package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class BotCombatListener implements Listener {

    private final Core plugin;
    private final Manager manager;

    public BotCombatListener(Core plugin, Manager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        BotSession playerSession = manager.getSession(target);
        if (playerSession != null) {
            playerSession.handlePlayerDamageEvent(event);
            return;
        }

        BotSession botSession = manager.getSessionByNpcUuid(target.getUniqueId());
        if (botSession != null) {
            botSession.handleBotDamageEvent(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        BotSession session = manager.getSession(event.getPlayer());
        if (session != null) session.handlePlayerLeave();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        BotSession session = manager.getSession(event.getPlayer());
        if (session != null) session.handlePlayerLeave();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        BotSession session = manager.getSession(event.getPlayer());
        if (session == null) return;
        if (session.shouldIgnoreWorldGuard(event.getTo())) return;
        session.handlePlayerLeave();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        BotSession session = manager.getSession(event.getPlayer());
        if (session == null) return;
        if (session.shouldIgnoreWorldGuard(event.getPlayer().getLocation())) return;
        session.handlePlayerLeave();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        // Check if the dying entity is a bot NPC first
        BotSession botSession = manager.getSessionByNpcUuid(event.getEntity().getUniqueId());
        if (botSession != null) {
            // Bot NPC died — suppress all drops and death message
            event.getDrops().clear();
            event.setDroppedExp(0);
            try { event.setDeathMessage(null); } catch (Throwable ignored) {}
            // Force-close the session since the bot entity is now dead
            botSession.forceClose(true, "bot-entity-death");
            return;
        }

        BotSession session = manager.getSession(event.getEntity());
        if (session != null) {
            session.handlePlayerDeath();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        BotSession session = manager.getSession(event.getPlayer());
        if (session != null) {
            plugin.getServer().getScheduler().runTask(plugin, session::handlePlayerLeave);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPotionSplash(PotionSplashEvent event) {
        BotSession session = manager.getSessionByTrackedPotion(event.getPotion().getUniqueId());
        if (session != null) {
            session.handlePotionSplash(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLingeringPotionSplash(LingeringPotionSplashEvent event) {
        BotSession session = manager.getSessionByTrackedPotion(event.getEntity().getUniqueId());
        if (session != null) {
            session.handleLingeringPotionSplash(event);
        }
    }
}
