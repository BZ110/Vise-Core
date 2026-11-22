package org.albi.core.modules.Duels.helpers;

import org.albi.core.data.Data;
import org.albi.core.serializable.SerializableItem;
import org.albi.core.util.KitSafety;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.Serializable;
import java.util.*;

public class Kit implements Serializable {

    private static final long serialVersionUID = 1L;          // unchanged

    /* ───────────────────────────────────────────────
     *  Core data
     * ───────────────────────────────────────────── */
    private String name;

    /** Main inventory: slot → item */
    private HashMap<Integer, SerializableItem> inventory   = new HashMap<>();

    /** NEW: Ender-chest contents: slot → item */
    private HashMap<Integer, SerializableItem> enderChest = new HashMap<>();   // NEW

    private SerializableItem displayItem;
    private int  slot;
    private boolean build;
    private boolean naturalRegen;
    private boolean healthIndics;
    private boolean loseHunger;
    private transient boolean isPk = false;
    private Data metaData = new Data();


    /* ───────────────────────────────────────────────
     *  Apply kit to a player
     * ───────────────────────────────────────────── */
    public void giveKit(Player p) {

        if (p == null) return;

        org.bukkit.inventory.PlayerInventory inv = p.getInventory();

        for (Map.Entry<Integer, SerializableItem> e : inventory.entrySet()) {
            if (e == null || e.getValue() == null) continue;
            int slot = e.getKey();
            org.bukkit.inventory.ItemStack item = KitSafety.sanitize(e.getValue().convertToItem());
            if (item == null) continue;

            org.albi.core.data.MainPlayer mp = org.albi.core.data.StatManagement.getMain(p);

            switch (slot) {
                case SLOT_BOOTS -> inv.setBoots(org.albi.core.util.ArmorTrimUtil.applyTrim(item, mp, "BOOTS"));
                case SLOT_LEGGINGS -> inv.setLeggings(org.albi.core.util.ArmorTrimUtil.applyTrim(item, mp, "LEGGINGS"));
                case SLOT_CHESTPLATE -> inv.setChestplate(org.albi.core.util.ArmorTrimUtil.applyTrim(item, mp, "CHESTPLATE"));
                case SLOT_HELMET -> inv.setHelmet(org.albi.core.util.ArmorTrimUtil.applyTrim(item, mp, "HELMET"));
                case SLOT_OFFHAND -> inv.setItemInOffHand(item);
                default -> inv.setItem(slot, item);
            }
        }

        if (enderChest != null && !enderChest.isEmpty()) {
            Inventory ec = p.getEnderChest();
            for (Map.Entry<Integer, SerializableItem> e : enderChest.entrySet()) {
                if (e == null || e.getValue() == null) continue;
                int slot = e.getKey();
                if (slot < 0 || slot >= ec.getSize()) continue;
                org.bukkit.inventory.ItemStack item = KitSafety.sanitize(e.getValue().convertToItem());
                if (item == null) continue;
                ec.setItem(slot, item);
            }
        }
    }

    /* ───────────────────────────────────────────────
     *  Getters & setters
     * ───────────────────────────────────────────── */
    public String                         getName()            { return name; }
    public int                            getSlot()            { return slot; }
    public HashMap<Integer, SerializableItem> getInventory()  { return inventory; }
    public HashMap<Integer, SerializableItem> getEnderChest() { return enderChest; }  // NEW
    public SerializableItem               getDisplayItem()     { return displayItem; }
    public boolean                        isBuild()            { return build; }
    public boolean                        isNaturalRegen()     { return naturalRegen; }
    public boolean                        isHealthIndics()     { return healthIndics; }
    public boolean                        isLoseHunger()       { return loseHunger; }
    public boolean                        isPk()               { return isPk; }

    public Data getMetaData() {
        if (metaData == null) metaData = new Data();
        return metaData;
    }

    public void setName(String name)                                  { this.name = name; }
    public void setSlot(int slot)                                     { this.slot = slot; }
    public void setInventory(HashMap<Integer, SerializableItem> inv)  { this.inventory = KitSafety.sanitizeMap(inv); }
    public void setEnderChest(Map<Integer, SerializableItem> chest)   { // NEW (reflection-friendly)
        this.enderChest = KitSafety.sanitizeMap(chest);
    }
    public void setDisplayItem(SerializableItem displayItem)          { this.displayItem = displayItem; }
    public void setNaturalRegen(boolean naturalRegen)                 { this.naturalRegen = naturalRegen; }
    public void setLoseHunger(boolean loseHunger)                     { this.loseHunger = loseHunger; }
    public void setBuild(boolean build)                               { this.build = build; }
    public void setPk(boolean pk)                                     { isPk = pk; }
    public void setHealthIndics(boolean healthIndics)                 { this.healthIndics = healthIndics; }

    public void setMetaData(Data metaData) {
        this.metaData = metaData;
    }

    /* ───────────────────────────────────────────────
     *  Debug / info dump
     * ───────────────────────────────────────────── */
    public String getInfo() {
        StringBuilder sb = new StringBuilder("=== Kit Detailed Information ===\n")
                .append("Name: ").append(name).append('\n');

        sb.append("Display Item: ").append(displayItem == null ? "None" : displayItem).append('\n');

        sb.append("Inventory Items:\n");
        if (inventory.isEmpty()) sb.append("  None\n");
        else new TreeMap<>(inventory).forEach((s, i) ->
                sb.append("  Slot ").append(s).append(": ").append(i).append('\n'));

        sb.append("Ender-Chest Items:\n");                                  // NEW
        if (enderChest == null || enderChest.isEmpty()) sb.append("  None\n");
        else new TreeMap<>(enderChest).forEach((s, i) ->
                sb.append("  Slot ").append(s).append(": ").append(i).append('\n'));

        sb.append("Build Enabled: ").append(build).append('\n')
                .append("Natural Regeneration: ").append(naturalRegen).append('\n')
                .append("Health Indicators: ").append(healthIndics).append('\n')
                .append("Lose Hunger: ").append(loseHunger).append('\n')
                .append("==================================");
        return sb.toString();
    }

    // ───────────────────────────────────────────────
// Armor / offhand slot indices (Spigot mapping)
// ───────────────────────────────────────────────
    public static final int SLOT_BOOTS      = 36;
    public static final int SLOT_LEGGINGS   = 37;
    public static final int SLOT_CHESTPLATE = 38;
    public static final int SLOT_HELMET     = 39;
    public static final int SLOT_OFFHAND    = 40;

    /* ───────────────────────────────────────────────
     * Armor / offhand getters (derived from inventory)
     * Return null if not present
     * ───────────────────────────────────────────── */
    public SerializableItem getHelmet()     { return inventory.get(SLOT_HELMET); }
    public SerializableItem getChestplate() { return inventory.get(SLOT_CHESTPLATE); }
    public SerializableItem getLeggings()   { return inventory.get(SLOT_LEGGINGS); }
    public SerializableItem getBoots()      { return inventory.get(SLOT_BOOTS); }
    public SerializableItem getOffhand()    { return inventory.get(SLOT_OFFHAND); }

    /* Optional: armor/offhand setters for convenience */
    public void setHelmet(SerializableItem it)     { putOrRemove(SLOT_HELMET, it); }
    public void setChestplate(SerializableItem it) { putOrRemove(SLOT_CHESTPLATE, it); }
    public void setLeggings(SerializableItem it)   { putOrRemove(SLOT_LEGGINGS, it); }
    public void setBoots(SerializableItem it)      { putOrRemove(SLOT_BOOTS, it); }
    public void setOffhand(SerializableItem it)    { putOrRemove(SLOT_OFFHAND, it); }

    private void putOrRemove(int slot, SerializableItem it) {
        if (it == null) inventory.remove(slot);
        else inventory.put(slot, it);
    }

    /* ───────────────────────────────────────────────
     * Non-armor/offhand contents (for editor "give kit without armor/offhand")
     * Returns a new map excluding armor+offhand slots
     * ───────────────────────────────────────────── */
    public Map<Integer, SerializableItem> getContentsWithoutArmorAndOffhand() {
        Map<Integer, SerializableItem> out = new HashMap<>();
        for (Map.Entry<Integer, SerializableItem> e : inventory.entrySet()) {
            int s = e.getKey();
            if (s == SLOT_BOOTS || s == SLOT_LEGGINGS || s == SLOT_CHESTPLATE || s == SLOT_HELMET || s == SLOT_OFFHAND)
                continue;
            out.put(s, e.getValue());
        }
        return out;
    }

    /* ───────────────────────────────────────────────
     * All items as a flat list (useful for multiset compares)
     * Includes armor/offhand
     * ───────────────────────────────────────────── */
    public List<SerializableItem> getAllItemsAsList() {
        return new ArrayList<>(inventory.values());
    }

}
