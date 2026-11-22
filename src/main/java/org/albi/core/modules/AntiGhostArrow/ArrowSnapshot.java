package org.albi.core.modules.AntiGhostArrow;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.util.Vector;

record ArrowSnapshot(Vector position, Vector velocity, int ticksLived, long capturedTick) {

    static ArrowSnapshot capture(AbstractArrow arrow, long tick) {
        if (arrow == null) {
            return null;
        }
        return new ArrowSnapshot(
                arrow.getLocation().toVector(),
                arrow.getVelocity().clone(),
                arrow.getTicksLived(),
                tick
        );
    }
}
