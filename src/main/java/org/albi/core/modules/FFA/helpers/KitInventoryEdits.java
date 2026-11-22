package org.albi.core.modules.FFA.helpers;

import org.albi.core.serializable.SerializableItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class KitInventoryEdits implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String kitName;

    /** Storage slots (0..35) only: slot -> SerializableItem */
    private HashMap<Integer, SerializableItem> inventory = new HashMap<>();

    /** Saved offhand item (from editor GUI slot 4). */
    private SerializableItem offhand; // NEW

    public KitInventoryEdits(String kitName) {
        this.kitName = kitName;
    }

    public String getKitName() { return kitName; }
    public Map<Integer, SerializableItem> getInventory() { return inventory; }

    /** Offhand helpers (NEW) */
    public void setOffhand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            this.offhand = null;
        } else {
            this.offhand = new SerializableItem(item);
        }
    }
    public ItemStack getOffhand() {
        return offhand == null ? null : offhand.convertToItem();
    }

    /** Set or remove a storage slot (0..35) */
    public void setSlot(int slot, ItemStack item) {
        if (slot < 0 || slot > 35) return; // guard: storage only
        if (item == null || item.getType() == Material.AIR) inventory.remove(slot);
        else inventory.put(slot, new SerializableItem(item));
    }

    /** Capture ENTIRE inventory – (kept for compatibility; not used by editor save) */
    public static KitInventoryEdits capture(String kitName, Inventory inv) {
        KitInventoryEdits snap = new KitInventoryEdits(kitName);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                // Only persist storage 0..35 for editor logic
                if (i >= 0 && i <= 35) {
                    snap.inventory.put(i, new SerializableItem(it));
                }
            }
        }
        return snap;
    }

    /** Capture ONLY storage (0..35). No armor, no offhand, no cursor. */
    public static KitInventoryEdits captureStorageOnly(String kitName, org.bukkit.inventory.PlayerInventory pinv) {
        KitInventoryEdits snap = new KitInventoryEdits(kitName);
        ItemStack[] storage = pinv.getStorageContents(); // 36 items (0..35)
        for (int i = 0; i < storage.length; i++) {
            ItemStack it = storage[i];
            if (it != null && it.getType() != Material.AIR) {
                snap.inventory.put(i, new SerializableItem(it));
            }
        }
        return snap;
    }


    // KitInventoryEdits
    public void applyToIncludingOffhand(Player p) {
        // storage (0..35)
        ItemStack[] empty = new ItemStack[36];
        p.getInventory().setStorageContents(empty);
        for (Map.Entry<Integer, SerializableItem> e : inventory.entrySet()) {
            int slot = e.getKey();
            if (slot >= 0 && slot <= 35) {
                ItemStack it = e.getValue().convertToItem();
                if (it != null && it.getType() != Material.AIR) {
                    p.getInventory().setItem(slot, it);
                }
            } else if (slot >= 36 && slot <= 39) {
                ItemStack it = e.getValue().convertToItem();
                if (it != null && it.getType() != Material.AIR) {
                    org.albi.core.data.MainPlayer mp = org.albi.core.data.StatManagement.getMain(p);
                    if (slot == 36) it = org.albi.core.util.ArmorTrimUtil.applyTrim(it, mp, "BOOTS");
                    else if (slot == 37) it = org.albi.core.util.ArmorTrimUtil.applyTrim(it, mp, "LEGGINGS");
                    else if (slot == 38) it = org.albi.core.util.ArmorTrimUtil.applyTrim(it, mp, "CHESTPLATE");
                    else if (slot == 39) it = org.albi.core.util.ArmorTrimUtil.applyTrim(it, mp, "HELMET");
                    p.getInventory().setItem(slot, it);
                }
            }
        }
        // offhand
        p.getInventory().setItemInOffHand(getOffhand());
        p.updateInventory();
    }

    public boolean isEmpty() {
        boolean noSlots = inventory == null || inventory.isEmpty();
        boolean noOffhand = (offhand == null);
        return noSlots && noOffhand;
    }

    public void applyToIncludingOffhandIfNonEmpty(Player p) {
        if (isEmpty()) return;
        applyToIncludingOffhand(p);
    }

}
