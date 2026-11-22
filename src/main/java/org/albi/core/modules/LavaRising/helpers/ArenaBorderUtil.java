package org.albi.core.modules.LavaRising.helpers;

import org.bukkit.Location;

public class ArenaBorderUtil {

    public static double calculateSize(double leastX, double mostX, double leastZ, double mostZ) {
        double width = Math.abs(mostX - leastX);
        double length = Math.abs(mostZ - leastZ);
        return Math.max(width, length);
    }

    public static Location calculateCenter(org.bukkit.World world, double leastX, double mostX, double leastZ, double mostZ) {
        double centerX = (leastX + mostX) / 2.0;
        double centerZ = (leastZ + mostZ) / 2.0;
        double centerY = world.getHighestBlockYAt((int) centerX, (int) centerZ) + 1; // Safe spot
        return new Location(world, centerX, centerY, centerZ);
    }
}
