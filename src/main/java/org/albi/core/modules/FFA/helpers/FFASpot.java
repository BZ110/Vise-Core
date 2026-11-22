package org.albi.core.modules.FFA.helpers;

import org.albi.core.serializable.SerializableItem;
import org.albi.core.serializable.SerializableLocation;

import java.io.Serial;
import java.io.Serializable;

public final class FFASpot implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private final int slot;                        // 0..8
    private final SerializableItem icon;           // menu icon
    private final SerializableLocation location;   // teleport target

    public FFASpot(int slot, SerializableItem icon, SerializableLocation loc) {
        this.slot = slot; this.icon = icon; this.location = loc;
    }

    public int getSlot() { return slot; }
    public SerializableItem getIcon() { return icon; }
    public SerializableLocation getLocation() { return location; }
}