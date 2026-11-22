package org.albi.core.modules.Duels.polar;

import org.albi.core.modules.Duels.helpers.Arena;
import org.bukkit.Location;

public final class PolarArenaReservation {

    private final Arena arena;
    private final int instanceId;
    private final String worldName;
    private final Location motherLocation;

    public PolarArenaReservation(Arena arena, int instanceId, String worldName, Location motherLocation) {
        this.arena = arena;
        this.instanceId = instanceId;
        this.worldName = worldName;
        this.motherLocation = motherLocation;
    }

    public Arena getArena() {
        return arena;
    }

    public int getInstanceId() {
        return instanceId;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getMotherLocation() {
        return motherLocation;
    }
}
