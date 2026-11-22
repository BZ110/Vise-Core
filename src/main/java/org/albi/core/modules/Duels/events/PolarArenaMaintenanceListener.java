package org.albi.core.modules.Duels.events;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PolarArenaMaintenanceListener implements Listener {

    private final Core plugin;

    public PolarArenaMaintenanceListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleMaintenance();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scheduleMaintenance();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleMaintenance();
    }

    private void scheduleMaintenance() {
        if (DuelManager.polarArenaManager == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> DuelManager.polarArenaManager.tickMaintenance(), 40L);
    }
}
