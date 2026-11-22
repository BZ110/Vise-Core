package org.albi.core.data;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;

public final class Cache {

    HashMap<Player, ExtendedPlayer> extension = new HashMap<>();

    public Cache() {

    }

    public HashMap<Player, ExtendedPlayer> getExtensions() {
        return extension;
    }

    public ExtendedPlayer addExtendedPlayer(Player p) {
        if(extension.containsKey(p)) {
            return extension.get(p);
        }

        extension.put(p, new ExtendedPlayer(p));
        return extension.get(p);
    }

    public ExtendedPlayer getExtendedPlayer(Player p) {
        return addExtendedPlayer(p);
    }

    public void removeExtendedPlayer(Player p) {
        extension.remove(p);
    }
}
