package org.albi.core.util;

import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.serializable.SerializableItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class KitSafety {

    private static final Set<Material> BLOCKED_MATERIALS = EnumSet.of(
            Material.BARRIER,
            Material.BEDROCK,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART
    );

    private KitSafety() {}

    public static boolean isBlocked(ItemStack item) {
        return item != null && BLOCKED_MATERIALS.contains(item.getType());
    }

    public static ItemStack sanitize(ItemStack item) {
        return isBlocked(item) ? null : item;
    }

    public static String queueDisplayName(Kit kit) {
        if (kit == null) return "Unknown Kit";
        if (kit.isPk()) return "Per Custom Kit";
        if (kit.getDisplayItem() != null) {
            try {
                ItemStack icon = kit.getDisplayItem().convertToItem();
                if (icon != null) {
                    ItemMeta meta = icon.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        return ChatColor.stripColor(meta.getDisplayName());
                    }
                }
            } catch (Exception ignored) {}
        }
        return kit.getName() == null || kit.getName().isBlank() ? "Unknown Kit" : kit.getName();
    }

    public static HashMap<Integer, SerializableItem> sanitizeMap(Map<Integer, SerializableItem> source) {
        HashMap<Integer, SerializableItem> out = new HashMap<>();
        if (source == null) return out;
        for (Map.Entry<Integer, SerializableItem> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) continue;
            try {
                ItemStack item = entry.getValue().convertToItem();
                if (isBlocked(item)) continue;
                out.put(entry.getKey(), entry.getValue());
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
