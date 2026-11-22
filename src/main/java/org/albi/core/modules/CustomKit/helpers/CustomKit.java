package org.albi.core.modules.CustomKit.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.albi.core.data.Data;                           // <-- NEW
import org.albi.core.data.EffectMeta;
import org.albi.core.modules.Duels.helpers.Kit;
import org.albi.core.serializable.SerializableItem;
import org.albi.core.util.KitSafety;
import org.bukkit.inventory.ItemStack;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class CustomKit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /* ───────────────────────────────────────────── */
    /* JSON Helper                                  */
    /* ───────────────────────────────────────────── */
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    public static CustomKit fromJson(String json) {
        return GSON.fromJson(json, CustomKit.class);
    }


    public String toJson() {
        return GSON.toJson(this);
    }

    /* ───────────────────────────────────────────── */
    /* Core Data                                    */
    /* ───────────────────────────────────────────── */
    private String name;
    private Map<Integer, SerializableItem> inventory = new HashMap<>();
    private Map<Integer, SerializableItem> enderChest = new HashMap<>();

    private SerializableItem displayItem;
    private int slot;
    private boolean build;
    private boolean naturalRegen;
    private boolean healthIndics;
    private boolean loseHunger;

    /** Flexible metadata container */                // <-- NEW
    private Data metaData = new Data();               // <-- NEW


    private static final String META_EFFECTS = "effects";

    /** Returns effects stored in metaData, always safe. */
    public List<EffectMeta> getEffects() {
        Object raw = getMetaData().get(META_EFFECTS);
        return EffectMeta.listFrom(raw);
    }



    /** Adds a new effect entry into metaData. */
    public void addEffect(EffectMeta effect) {
        if (effect == null) return;
        List<EffectMeta> list = getEffects();
        list.add(effect);
        getMetaData().set(META_EFFECTS, EffectMeta.listToMaps(list));
    }

    /** Removes effect by index, returns true if removed. */
    public boolean removeEffect(int index) {
        List<EffectMeta> list = getEffects();
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        getMetaData().set(META_EFFECTS, EffectMeta.listToMaps(list));
        return true;
    }

    /** Clears all effects. */
    public void clearEffects() {
        getMetaData().set(META_EFFECTS, new ArrayList<>());
    }


    /* ───────────────────────────────────────────── */
    /* Constructors                                 */
    /* ───────────────────────────────────────────── */
    public CustomKit() {}

    public CustomKit(String name) {
        this.name = name;
    }

    /* ───────────────────────────────────────────── */
    /* Conversion                                   */
    /* ───────────────────────────────────────────── */
    public Kit toKit() {
        Kit k = new Kit();
        k.setName(name);
        k.setInventory(KitSafety.sanitizeMap(inventory));

        try {
            k.getClass()
                    .getMethod("setEnderChest", Map.class)
                    .invoke(k, KitSafety.sanitizeMap(enderChest));
        } catch (Exception ignored) {}

        k.setDisplayItem(displayItem);
        k.setSlot(slot);
        k.setBuild(build);
        k.setNaturalRegen(naturalRegen);
        k.setHealthIndics(healthIndics);
        k.setLoseHunger(loseHunger);
        k.setMetaData(metaData);

        return k;
    }

    /* ───────────────────────────────────────────── */
    /* Inventory Helpers                            */
    /* ───────────────────────────────────────────── */
    public void setSlot(int slotIndex, ItemStack item) {
        if (item == null) inventory.remove(slotIndex);
        else inventory.put(slotIndex, new SerializableItem(item));
    }


    public SerializableItem getSlot(int slot) { return inventory.get(slot); }
    public SerializableItem getEnderSlot(int slot) { /* <-- NEW */ return enderChest.get(slot); }

    public ItemStack[] getDirectKit() {
        int size = inventory.keySet().stream()
                .mapToInt(Integer::intValue)
                .max().orElse(35) + 1;

        ItemStack[] items = new ItemStack[size];

        for (Map.Entry<Integer, SerializableItem> e : inventory.entrySet()) {
            int idx = e.getKey();
            if (idx < 0 || idx >= size) continue;
            items[idx] = e.getValue() != null
                    ? e.getValue().convertToItem()
                    : null;
        }

        return items;
    }

    /* ───────────────────────────────────────────── */
    /* Ender Chest                                  */
    /* ───────────────────────────────────────────── */
    public void setEnderSlot(int slotIndex, ItemStack item) {
        if (item == null) enderChest.remove(slotIndex);
        else enderChest.put(slotIndex, new SerializableItem(item));
    }

    public ItemStack[] getDirectEnderChest() {
        ItemStack[] items = new ItemStack[27];

        for (Map.Entry<Integer, SerializableItem> e : enderChest.entrySet()) {
            int idx = e.getKey();
            if (idx < 0 || idx >= 27) continue;
            items[idx] = e.getValue() != null
                    ? e.getValue().convertToItem()
                    : null;
        }

        return items;
    }

    /* ───────────────────────────────────────────── */
    /* MetaData API                                 */
    /* ───────────────────────────────────────────── */

    public Data getMetaData() {
        if (metaData == null) metaData = new Data();   // backward safety
        return metaData;
    }

    public void setMetaData(Data metaData) {
        this.metaData = metaData;
    }

    /* ───────────────────────────────────────────── */
    /* Getters / Setters                            */
    /* ───────────────────────────────────────────── */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<Integer, SerializableItem> getInventory() {
        return Collections.unmodifiableMap(inventory);
    }

    public void setInventory(Map<Integer, SerializableItem> inv) {
        this.inventory = new HashMap<>(inv);
    }

    public Map<Integer, SerializableItem> getEnderChest() {
        return Collections.unmodifiableMap(enderChest);
    }

    public void setEnderChest(Map<Integer, SerializableItem> chest) {
        this.enderChest = new HashMap<>(chest);
    }

    public SerializableItem getDisplayItem() { return displayItem; }
    public void setDisplayItem(SerializableItem display) { this.displayItem = display; }

    public boolean isBuild() { return build; }
    public void setBuild(boolean build) { this.build = build; }

    public boolean isNaturalRegen() { return naturalRegen; }
    public void setNaturalRegen(boolean naturalRegen) { this.naturalRegen = naturalRegen; }

    public boolean isHealthIndics() { return healthIndics; }
    public void setHealthIndics(boolean healthIndics) { this.healthIndics = healthIndics; }

    public boolean isLoseHunger() { return loseHunger; }
    public void setLoseHunger(boolean loseHunger) { this.loseHunger = loseHunger; }
}
