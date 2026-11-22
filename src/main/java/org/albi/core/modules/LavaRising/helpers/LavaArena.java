package org.albi.core.modules.LavaRising.helpers;

import org.albi.core.modules.LavaRising.LavaRisingManager;
import org.albi.core.serializable.SerializableLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.Serializable;

public class LavaArena implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String schematic;
    private SerializableLocation spawn;

    private double leastX;
    private double leastZ;
    private double mostX;
    private double mostZ;

    public void setName(String name) {
        this.name = name;
    }

    public void setSchematic(String schematic) {
        this.schematic = schematic;
    }

    public void setPositions(double leastX, double mostX, double leastZ, double mostZ) {
        this.leastX = leastX;
        this.leastZ = leastZ;
        this.mostX = mostX;
        this.mostZ = mostZ;
    }

    public boolean isLocationInArea(Location loc) {
        double x = loc.getX();
        double z = loc.getZ();

        return x >= leastX && x <= mostX && z >= leastZ && z <= mostZ;
    }

    public void teleportPlayerToSpawn(Player player) {
        player.teleportAsync(spawn.convertToLocation());
    }

    public void setSpawn(SerializableLocation spawn) {
        this.spawn = spawn;
    }


    public String getName() {
        return name;
    }

    public String getSchematic() {
        return schematic;
    }

    public File getSchematicFile() {
        return LavaRisingManager.generateLavaRisingSchematic(schematic);
    }

    public void pasteSchematic(Location loc, boolean air) {
        LavaRisingManager.schematicManager.enqueueSchematic(loc, getSchematicFile(), air);
    }

    public void sendWorldBorder(Player player) {
        Location center = calculateCenter(player.getWorld());
        double size = calculateSize();

        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(center);
        border.setSize(size);
        border.setWarningDistance(1); // Optional: warning red glow
        border.setDamageBuffer(0.25);    // Optional: buffer before damage
        border.setDamageAmount(0.5);    // Optional: damage outside

        player.setWorldBorder(border);
    }


    private double calculateSize() {
        double width = Math.abs(mostX - leastX);
        double length = Math.abs(mostZ - leastZ);
        return Math.max(width, length);
    }

    private Location calculateCenter(World world) {
        double centerX = (leastX + mostX) / 2.0;
        double centerZ = (leastZ + mostZ) / 2.0;
        double centerY = world.getHighestBlockYAt((int) centerX, (int) centerZ) + 1;
        return new Location(world, centerX, centerY, centerZ);
    }


    public double getLeastX() { return leastX; }
    public double getMostX() { return mostX; }
    public double getLeastZ() { return leastZ; }
    public double getMostZ() { return mostZ; }

    public SerializableLocation getSpawn() {
        return spawn;
    }
}
