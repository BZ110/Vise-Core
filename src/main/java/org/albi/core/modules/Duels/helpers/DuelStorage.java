package org.albi.core.modules.Duels.helpers;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Holds every duel arena and every regular kit that exists on this server.
 */
public class DuelStorage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /* Public so they can be JSON‑serialised by Gson, but treat via helpers. */
    public final List<Arena> arenas = new ArrayList<>();
    public final List<Kit>   kits   = new ArrayList<>();
    public Map<String, List<String>> rankedArenas = new HashMap<>(); // KitName -> List of ArenaNames

    public Map<String, List<String>> getRankedArenas() {
        if (rankedArenas == null) rankedArenas = new HashMap<>();
        return rankedArenas;
    }

    /* ─────────────────────────────────────────── */
    /*  Kit helpers                                */
    /* ─────────────────────────────────────────── */

    /** Unmodifiable view of all registered kits (regular kits only). */
    public List<Kit> getAllKits() {
        return Collections.unmodifiableList(kits);
    }

    /** Case‑insensitive lookup. Returns null if not found. */
    public Kit getKitByName(String name) {
        for (Kit k : kits) {
            if (k.getName().equalsIgnoreCase(name)) return k;
        }
        return null;
    }

    /** Add/replace a kit by name. */
    public void registerKit(Kit kit) {
        removeKit(kit.getName());
        kits.add(kit);
    }

    /** Remove kit by name; returns true if something was removed. */
    public boolean removeKit(String name) {
        return kits.removeIf(k -> k.getName().equalsIgnoreCase(name));
    }

    /* ─────────────────────────────────────────── */
    /*  Arena helpers                              */
    /* ─────────────────────────────────────────── */

    /** Unmodifiable view of all arenas. */
    public List<Arena> getAllArenas() {
        return Collections.unmodifiableList(arenas);
    }

    /** Case‑insensitive lookup. */
    public Arena getArenaByName(String name) {
        for (Arena a : arenas) {
            if (a.getName().equalsIgnoreCase(name)) return a;
        }
        return null;
    }

    /** Register or replace an arena. */
    public void registerArena(Arena arena) {
        removeArena(arena.getName());
        arenas.add(arena);
    }

    /** Remove arena by name; returns true if removed. */
    public boolean removeArena(String name) {
        return arenas.removeIf(a -> a.getName().equalsIgnoreCase(name));
    }
}
