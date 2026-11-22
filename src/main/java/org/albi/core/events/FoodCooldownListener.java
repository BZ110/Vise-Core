package org.albi.core.events;

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

    private final Map<UUID, Long> nextEat = new HashMap<>();
    private final int cooldownSeconds = 10;

    public FoodCooldownListener(Core plugin) {

    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.RAW_GOLD) return;

        Player player = e.getPlayer();
        long now = System.currentTimeMillis();
        long allowedAt = nextEat.getOrDefault(player.getUniqueId(), 0L);

        if (now < allowedAt) {
            long remain = (allowedAt - now + 999) / 1000;
            player.sendMessage(ChatColor.RED + "You must wait " + remain + " seconds before you can eat another.");
            e.setCancelled(true);
            return;
        }

        nextEat.put(player.getUniqueId(), now + cooldownSeconds * 1000L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        nextEat.remove(e.getPlayer().getUniqueId());
    }
}