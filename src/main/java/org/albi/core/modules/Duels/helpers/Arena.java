package org.albi.core.modules.Duels.helpers;

import org.albi.core.serializable.SerializableItem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Arena implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private SerializableItem displayItem;
    private String schematic;
    private ArrayList<Kit> allowedKits;
    private HashMap<Integer, Integer> slotKits = new HashMap<>();
    private int offsetZAxis;
    private int offsetXAxis;
    private int maxPerLine;
    private int maxArenas;
    private boolean polarArena;
    public boolean deactivated;


    // Spawn coordinates for two teams (X, Y, Z, Yaw, Pitch)
    private double[] spawn1 = new double[5];
    private double[] spawn2 = new double[5];

    // Corner parameters: x1, z1, x2, z2
    private double[] cornerParameters = new double[4];

    /**
     * Default constructor. Initializes the allowedKits list.
     */
    public Arena() {
        allowedKits = new ArrayList<>();
    }

    // -------------------------
    // Getters
    // -------------------------

    public String getName() {
        return name;
    }

    public SerializableItem getDisplayItem() {
        return displayItem;
    }

    public String getSchematic() {
        return schematic;
    }

    public ArrayList<org.albi.core.modules.Duels.helpers.Kit> getAllowedKits() {
        return allowedKits;
    }

    public HashMap<Integer, Integer> getSlotKits() {
        return slotKits;
    }

    public int getOffsetZAxis() {
        return offsetZAxis;
    }

    public int getOffsetXAxis() {
        return offsetXAxis;
    }

    public int getMaxPerLine() {
        return maxPerLine;
    }

    public int getMaxArenas() {
        return maxArenas;
    }

    public boolean isPolarArena() {
        return polarArena;
    }

    public double[] getSpawn1() {
        return spawn1;
    }

    public double[] getSpawn2() {
        return spawn2;
    }

    public double[] getCornerParameters() {
        return cornerParameters;
    }

    // -------------------------
    // Setters
    // -------------------------

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayItem(SerializableItem displayItem) {
        this.displayItem = displayItem;
    }

    public void setSchematic(String schematic) {
        this.schematic = schematic;
    }

    public void setOffsetZAxis(int offsetZAxis) {
        this.offsetZAxis = offsetZAxis;
    }

    public void setOffsetXAxis(int offsetXAxis) {
        this.offsetXAxis = offsetXAxis;
    }

    public void setMaxPerLine(int maxPerLine) {
        this.maxPerLine = maxPerLine;
    }

    public void setMaxArenas(int maxArenas) {
        this.maxArenas = maxArenas;
    }

    public void setPolarArena(boolean polarArena) {
        this.polarArena = polarArena;
    }

    /**
     * Adds a Kit to the list of allowed kits.
     *
     * @param kit The Kit to add.
     */
    public void addAllowedKit(org.albi.core.modules.Duels.helpers.Kit kit) {
        allowedKits.add(kit);
    }

    /**
     * Adds a slot to the list of allowed kits.
     *
     * @param kit The Kit to add.
     */
    public void addSlotKit(org.albi.core.modules.Duels.helpers.Kit kit, int x) {
        // find the index.
        int index = allowedKits.indexOf(kit);

        // put it in the list.
        slotKits.put(index, x);
    }

    public int getSlotKit(org.albi.core.modules.Duels.helpers.Kit kit) {
        // Find the index.
        int index = allowedKits.indexOf(kit);

        // return the value.
        return slotKits.get(index);
    }

    /**
     * Removes a Kit from the list of allowed kits.
     *
     * @param kit The Kit to remove.
     */
    public void removeAllowedKit(org.albi.core.modules.Duels.helpers.Kit kit) {
        allowedKits.remove(kit);
    }

    /**
     * Sets a spawn coordinate for a given team.
     *
     * @param team  The team number (1 or 2).
     * @param type  The coordinate type ("x", "y", "z", "yaw", or "pitch").
     * @param value The value to set.
     */
    public void setSpawn(int team, String type, double value) {
        double[] spawn;
        if (team == 1) {
            spawn = spawn1;
        } else if (team == 2) {
            spawn = spawn2;
        } else {
            // Invalid team number; do nothing.
            return;
        }

        switch (type.toLowerCase()) {
            case "x":
                spawn[0] = value;
                break;
            case "y":
                spawn[1] = value;
                break;
            case "z":
                spawn[2] = value;
                break;
            case "yaw":
                spawn[3] = value;
                break;
            case "pitch":
                spawn[4] = value;
                break;
            default:
                // Unrecognized type; do nothing.
                break;
        }
    }

    /**
     * Sets a corner parameter value.
     *
     * @param type  The parameter type ("x1", "z1", "x2", or "z2").
     * @param value The value to set.
     */
    public void setCornerParameter(String type, double value) {
        switch (type.toLowerCase()) {
            case "x1":
                cornerParameters[0] = value;
                break;
            case "z1":
                cornerParameters[1] = value;
                break;
            case "x2":
                cornerParameters[2] = value;
                break;
            case "z2":
                cornerParameters[3] = value;
                break;
            default:
                // Unrecognized parameter type; do nothing.
                break;
        }
    }

    public String toString() {
        return name;
    }

    public String getInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Arena Detailed Information ===\n");
        info.append("Name: ").append(name).append("\n");
        info.append("Schematic: ").append(schematic).append("\n");
        info.append("OffsetZAxis: ").append(offsetZAxis).append("\n");
        info.append("OffsetXAxis: ").append(offsetXAxis).append("\n");
        info.append("MaxPerLine: ").append(maxPerLine).append("\n");
        info.append("Max Arenas: ").append(maxArenas).append("\n");
        info.append("Polar Arena: ").append(polarArena).append("\n");

        info.append("Display Item: ");
        if (displayItem != null) {
            info.append(displayItem).append("\n");
        } else {
            info.append("None\n");
        }

        info.append("Allowed Kits:\n");
        if (allowedKits != null && !allowedKits.isEmpty()) {
            for (int i = 0; i < allowedKits.size(); i++) {
                org.albi.core.modules.Duels.helpers.Kit kit = allowedKits.get(i);
                info.append("  ").append(i + 1).append(". ").append(kit.toString());
                if (slotKits != null && slotKits.containsKey(i)) {
                    info.append(" (Slot: ").append(slotKits.get(i)).append(")");
                }
                info.append("\n");
            }
        } else {
            info.append("  None\n");
        }

        info.append("Team 1 Spawn (X, Y, Z, Yaw, Pitch): ");
        info.append(String.format("%.2f, %.2f, %.2f, %.2f, %.2f", spawn1[0], spawn1[1], spawn1[2], spawn1[3], spawn1[4])).append("\n");

        info.append("Team 2 Spawn (X, Y, Z, Yaw, Pitch): ");
        info.append(String.format("%.2f, %.2f, %.2f, %.2f, %.2f", spawn2[0], spawn2[1], spawn2[2], spawn2[3], spawn2[4])).append("\n");

        info.append("Corner Parameters (x1, z1, x2, z2): ");
        info.append(String.format("%.2f, %.2f, %.2f, %.2f", cornerParameters[0], cornerParameters[1], cornerParameters[2], cornerParameters[3])).append("\n");

        info.append("==================================");
        return info.toString();
    }

}
