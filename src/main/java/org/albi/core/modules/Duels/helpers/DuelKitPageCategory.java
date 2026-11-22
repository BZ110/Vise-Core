package org.albi.core.modules.Duels.helpers;

import org.albi.core.serializable.SerializableItem;

import java.io.Serial;
import java.io.Serializable;

public class DuelKitPageCategory implements Serializable {
    private int page;
    private int guiSlot;
    private SerializableItem displayItem;
    @Serial private static final long serialVersionUID = 1L;


    public DuelKitPageCategory() {}

    public DuelKitPageCategory(int page, int guiSlot, SerializableItem displayItem) {
        this.page = page;
        this.guiSlot = guiSlot;
        this.displayItem = displayItem;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getGuiSlot() { return guiSlot; }
    public void setGuiSlot(int guiSlot) { this.guiSlot = guiSlot; }
    public SerializableItem getDisplayItem() { return displayItem; }
    public void setDisplayItem(SerializableItem displayItem) { this.displayItem = displayItem; }
}
