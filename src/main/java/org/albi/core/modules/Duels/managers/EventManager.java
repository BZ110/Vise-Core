package org.albi.core.modules.Duels.managers;

import org.albi.core.Core;
import org.albi.core.modules.Duels.events.*;
import org.bukkit.event.Listener;

public class EventManager {
    public EventManager(Core plugin) {
        registerEvent("world.change", new OnWorldChangeListener(plugin), plugin);
        registerEvent("movement", new MovementOutside(plugin), plugin);
        registerEvent("bed.expl", new OnBedExplode(plugin), plugin);
        registerEvent("block", new OnBlockModification(plugin), plugin);
        registerEvent("damage", new OnDamage(plugin), plugin);
        registerEvent("hp.food", new OnHealthAndFood(plugin), plugin);
        registerEvent("sim.death", new OnSimulatedDeath(plugin), plugin);
        registerEvent("mend", new OnMend(plugin), plugin);
        registerEvent("polar.maintenance", new PolarArenaMaintenanceListener(plugin), plugin);
    }

    private void registerEvent(String name, Listener event, Core plugin) {
        try {
            plugin.getLogger().info("[Duels] Registering Event: " + name);
            plugin.getServer().getPluginManager().registerEvents(event, plugin);
        } catch (Exception e) {
            plugin.getLogger().severe("[Duels] Could not register event: " + name);
        }
    }
}