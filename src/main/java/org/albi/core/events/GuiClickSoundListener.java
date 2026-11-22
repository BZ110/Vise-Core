package org.albi.core.events;

import org.albi.core.util.GuiSoundUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class GuiClickSoundListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView() == null || event.getClickedInventory() == null) return;
        if (event.getClickedInventory().getType() == InventoryType.PLAYER) return;
        String title = event.getView().getTitle();
        if (title == null) return;
        String key = title.toLowerCase(Locale.ROOT);
        if (!(key.contains("queue") || key.contains("duel") || key.contains("party") || key.contains("friends")
                || key.contains("settings") || key.contains("kit") || key.contains("draft") || key.contains("selector")
                || key.contains("browser") || key.contains("teleporter") || key.contains("travel") || key.contains("stats"))) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        Material type = item == null ? Material.AIR : item.getType();
        if (type == Material.BARRIER || type == Material.RED_CONCRETE || type == Material.RED_STAINED_GLASS_PANE) {
            GuiSoundUtil.negative(player);
            return;
        }
        if (type == Material.EMERALD || type == Material.LIME_CONCRETE || type == Material.DIAMOND_SWORD
                || type == Material.NETHER_STAR || type == Material.GOAT_HORN || type == Material.PLAYER_HEAD) {
            GuiSoundUtil.positive(player);
            return;
        }
        if (type == Material.BOOK || type == Material.WRITABLE_BOOK || type == Material.PAPER || type == Material.COMPARATOR
                || type == Material.CRAFTER || type == Material.ANVIL) {
            GuiSoundUtil.page(player);
            return;
        }
        GuiSoundUtil.click(player);
    }
}
