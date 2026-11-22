package org.albi.core.modules.Duels.helpers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

public class FlexibleInventory {

    /**
     * Slot encoding<br>
     *  • 0-35  Main inventory + hot-bar<br>
     *  • 40    Off-hand<br>
     *  • 100-103 Armour (helmet→boots)<br>
     *  • 200-226 Ender chest (27 slots)       // NEW
     */
    private final Map<Integer, ItemStack> inventoryMap = new HashMap<>();
    private ItemStack cursor;

    /* ------------------------------------------------------------------ */
    /*  CAPTURE                                                           */
    /* ------------------------------------------------------------------ */
    public FlexibleInventory captureInventory(Player p) {

        PlayerInventory inv = p.getInventory();

        /* main + hot-bar ------------------------------------------------ */
        ItemStack[] contents = inv.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            inventoryMap.put(slot, cloneOrAir(contents[slot]));
        }

        /* off-hand ------------------------------------------------------ */
        inventoryMap.put(40, cloneOrAir(inv.getItemInOffHand()));

        /* armour -------------------------------------------------------- */
        inventoryMap.put(100, cloneOrAir(inv.getHelmet()));
        inventoryMap.put(101, cloneOrAir(inv.getChestplate()));
        inventoryMap.put(102, cloneOrAir(inv.getLeggings()));
        inventoryMap.put(103, cloneOrAir(inv.getBoots()));

        /* ender chest --------------------------------------------------- */  // NEW
        Inventory ec = p.getEnderChest();
        ItemStack[] ecItems = ec.getContents();              // always length 27
        for (int slot = 0; slot < ecItems.length; slot++) {
            inventoryMap.put(200 + slot, cloneOrAir(ecItems[slot]));
        }

        /* cursor item --------------------------------------------------- */
        InventoryView view = p.getOpenInventory();
        cursor = cloneOrAir(view.getCursor());

        return this;
    }

    /* ------------------------------------------------------------------ */
    /*  GIVE BACK                                                         */
    /* ------------------------------------------------------------------ */
    public FlexibleInventory giveInventory(Player p) {

        PlayerInventory inv = p.getInventory();

        /* main + hot-bar ------------------------------------------------ */
        ItemStack[] main = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            main[i] = inventoryMap.getOrDefault(i, new ItemStack(Material.AIR));
        }
        inv.setStorageContents(main);

        /* off-hand & armour -------------------------------------------- */
        inv.setItemInOffHand(inventoryMap.getOrDefault(40, new ItemStack(Material.AIR)));
        inv.setHelmet(    inventoryMap.getOrDefault(100, null));
        inv.setChestplate(inventoryMap.getOrDefault(101, null));
        inv.setLeggings(  inventoryMap.getOrDefault(102, null));
        inv.setBoots(     inventoryMap.getOrDefault(103, null));

        /* ender chest --------------------------------------------------- */  // NEW
        Inventory ec = p.getEnderChest();
        ItemStack[] ecRestore = new ItemStack[27];
        for (int slot = 0; slot < 27; slot++) {
            ecRestore[slot] = inventoryMap.getOrDefault(200 + slot, new ItemStack(Material.AIR));
        }
        ec.setContents(ecRestore);

        /* cursor -------------------------------------------------------- */
        p.getOpenInventory().setCursor(cursor);

        return this;
    }

    /* ------------------------------------------------------------------ */
    private static ItemStack cloneOrAir(ItemStack in) {
        return in == null || in.getType() == Material.AIR
                ? new ItemStack(Material.AIR)
                : in.clone();
    }
}
