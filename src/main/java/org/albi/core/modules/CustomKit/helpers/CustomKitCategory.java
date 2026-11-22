package org.albi.core.modules.CustomKit.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.albi.core.serializable.SerializableItem;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** One category in the Custom‑Kit picker system. */
public class CustomKitCategory implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static CustomKitCategory fromJson(String json) { return GSON.fromJson(json, CustomKitCategory.class); }
    public String toJson()                                  { return GSON.toJson(this); }

    private String name;
    private SerializableItem displayItem;                   // logo in Category GUI
    private int categoryGuiSlot;                            // slot in the Category GUI (0‑53)
    private Map<Integer, SerializableItem> items = new HashMap<>(); // slot → item

    public CustomKitCategory() {} // for Gson

    public CustomKitCategory(String name, int guiSlot, SerializableItem logo) {
        this.name = name;
        this.categoryGuiSlot = guiSlot;
        this.displayItem = logo;
    }

    // Simple Getters / Setters
    public String getName()                        { return name; }
    public int getCategoryGuiSlot()                { return categoryGuiSlot; }
    public void setCategoryGuiSlot(int s)          { this.categoryGuiSlot = s; }

    public SerializableItem getDisplayItem()       { return displayItem; }
    public void setDisplayItem(SerializableItem si){ this.displayItem = si; }

    // Alias for better readability
    public SerializableItem getIcon()              { return displayItem; }
    public void setIcon(SerializableItem si)       { this.displayItem = si; }

    public Map<Integer, SerializableItem> getItems() { return items; }

    public void setItem(int guiSlot, SerializableItem item) {
        if (item == null) items.remove(guiSlot);
        else              items.put(guiSlot, item);
    }

    // For listing
    public int getSlot() {
        return categoryGuiSlot;
    }
}
