package org.albi.core.modules.FFA.helpers;

import org.albi.core.serializable.SerializableItem;
import org.albi.core.serializable.SerializableLocation;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/** Root storage for all FFA configuration. */
public final class FFAStorage implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** kitName(lowercase) -> setup */
    private final Map<String, FFAKitSetup> setups = new HashMap<>();

    public Collection<FFAKitSetup> allSetups() { return setups.values(); }

    public FFAKitSetup getOrCreate(String kitName) {
        FFAKitSetup s = setups.get(kitName.toLowerCase());
        if (s == null) {
            s = new FFAKitSetup(kitName);
            setups.put(kitName.toLowerCase(), s);
        }
        return s;
    }

    public FFAKitSetup get(String kitName) { return setups.get(kitName.toLowerCase()); }

    public boolean has(String kitName) { return setups.containsKey(kitName.toLowerCase()); }

    public void remove(String kitName) { setups.remove(kitName.toLowerCase()); }
}