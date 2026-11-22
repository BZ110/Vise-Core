package org.albi.core.modules.FFA.helpers;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public final class FFAKitSetup implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private final String kitName;              // references Duels.Kit by name
    private String worldName;                  // target world
    private boolean allowBuild;
    private boolean naturalRegen;
    private boolean healthIndicators;
    private boolean loseHunger;

    /** slot -> spot */
    private final Map<Integer, FFASpot> spots = new HashMap<>();

    public FFAKitSetup(String kitName) { this.kitName = kitName; }

    public String getKitName() { return kitName; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public boolean isAllowBuild() { return allowBuild; }
    public void setAllowBuild(boolean allowBuild) { this.allowBuild = allowBuild; }

    public boolean isNaturalRegen() { return naturalRegen; }
    public void setNaturalRegen(boolean naturalRegen) { this.naturalRegen = naturalRegen; }

    public boolean isHealthIndicators() { return healthIndicators; }
    public void setHealthIndicators(boolean healthIndicators) { this.healthIndicators = healthIndicators; }

    public boolean isLoseHunger() { return loseHunger; }
    public void setLoseHunger(boolean loseHunger) { this.loseHunger = loseHunger; }

    public Map<Integer, FFASpot> getSpots() { return spots; }

    public void setSpot(int slot, FFASpot spot) {
        if (slot < 0 || slot > 8) return;
        spots.put(slot, spot);
    }
}