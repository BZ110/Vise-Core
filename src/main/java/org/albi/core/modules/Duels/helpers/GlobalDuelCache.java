package org.albi.core.modules.Duels.helpers;

import org.albi.core.modules.Duels.DuelInstance;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;

public class GlobalDuelCache {

    public static HashMap<Arena, ArrayList<Location>> availableArenas = new HashMap<>();
    public static HashMap<Arena, ArrayList<Location>> usedArenas = new HashMap<>();

    public static ArrayList<DuelInstance> availableInstances = new ArrayList<>();
}
