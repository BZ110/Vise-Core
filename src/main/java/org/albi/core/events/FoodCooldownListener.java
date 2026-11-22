package org.albi.core.events;

import org.albi.core.gui.Messages;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import org.albi.core.Core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FoodCooldownListener implements Listener {

    private final Core plugin;
    private final Map<UUID, Map<Material, Long>> nextEat = new HashMap<>();

    public FoodCooldownListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;
        long cooldown = plugin.getCombatSettings().foodCooldowns().getOrDefault(item.getType(), 0L);
        if (cooldown == 0) return;

        Player player = e.getPlayer();
        var times = nextEat.computeIfAbsent(player.getUniqueId(), ignored -> new java.util.EnumMap<>(Material.class));
        long now = System.currentTimeMillis();
        long allowedAt = times.getOrDefault(item.getType(), 0L);

        if (now < allowedAt) {
            long remain = (allowedAt - now + 999) / 1000;
            Messages.send(player, "messages.food-cooldown-listener.you-must-wait-seconds-before-you-can-eat", ChatColor.RED, remain);
            e.setCancelled(true);
            return;
        }

        times.put(item.getType(), now + cooldown);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        nextEat.remove(e.getPlayer().getUniqueId());
    }
}