package org.albi.core.util;

import org.albi.core.data.MainPlayer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

public class ArmorTrimUtil {

    public static ItemStack applyTrim(ItemStack item, MainPlayer profile, String slotName) {
        if (item == null || item.getType() == Material.AIR) return item;
        if (!item.getType().name().endsWith("_" + slotName)) return item;
        
        String comboId = profile.getArmorTrims().get(slotName);
        if (comboId == null || comboId.isEmpty()) return item;

        try {
            TrimCombo combo = TrimCombo.valueOf(comboId);
            return applySpecificTrim(item, combo);
        } catch (Exception e) {
            // Ignore if combo is invalid or removed
        }
        
        return item;
    }

    public static ItemStack applySpecificTrim(ItemStack item, TrimCombo combo) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(combo.getMaterial(), combo.getPattern()));
            item.setItemMeta(meta);
        }
        return item;
    }
}
