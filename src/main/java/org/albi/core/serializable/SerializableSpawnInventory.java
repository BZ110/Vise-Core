package org.albi.core.serializable;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;

public class SerializableSpawnInventory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private HashMap<Integer, SerializableItem> inventory = new HashMap<>();

    public void addItem(int slot, ItemStack item, String command) {
        inventory.put(slot, new SerializableItem(item, command));
    }

    public void removeItem(int slot) {
        inventory.remove(slot);
    }
    
    public String getCommand(int slot) {
        return (String) inventory.get(slot).getExtraValue();
    }

    public String getCommand(ItemStack item) {
        if (item == null) return null;

        for (SerializableItem sItem : inventory.values()) {
            ItemStack stored = sItem.convertToItem();
            if (stored != null && stored.isSimilar(item)) {
                return (String) sItem.getExtraValue();
            }
        }

        return null; // Not found
    }

    public void givePlayer(Player p) {
        if (p == null || inventory.isEmpty()) return;
        p.getInventory().clear();
        for (HashMap.Entry<Integer, SerializableItem> entry : inventory.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue().convertToItem();

            if (item != null && slot >= 0 && slot < p.getInventory().getSize()) {
                p.getInventory().setItem(slot, item);
            }
        }
    }

}


