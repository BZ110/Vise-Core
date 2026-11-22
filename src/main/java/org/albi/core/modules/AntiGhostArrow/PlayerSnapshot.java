package org.albi.core.modules.AntiGhostArrow;

import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

record PlayerSnapshot(BoundingBox boundingBox, Vector velocity) {

    static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player.getBoundingBox().clone(), player.getVelocity().clone());
    }
}
