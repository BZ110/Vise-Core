package org.albi.core.serializable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.Serializable;

public class SerializableLocation implements Serializable {
    private static final long serialVersionUID = 1L;

    private double x;
    private double y;
    private double z;
    private double yaw;
    private double pitch;
    private String worldName;

    public SerializableLocation() {
    }

    public SerializableLocation(Location loc) {
        convertFromLocation(loc);
    }

    public void convertFromLocation(Location loc) {
        x = loc.getX();
        y = loc.getY();
        z = loc.getZ();
        yaw = loc.getYaw();
        pitch = loc.getPitch();
        worldName = loc.getWorld().getName();
    }

    public Location convertToLocation() {
        if (worldName == null || worldName.isEmpty()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        return new Location(world, x, y, z, (float) yaw, (float) pitch);
    }

    // Getters and setters
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public double getYaw() { return yaw; }
    public void setYaw(double yaw) { this.yaw = yaw; }

    public double getPitch() { return pitch; }
    public void setPitch(double pitch) { this.pitch = pitch; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
}
