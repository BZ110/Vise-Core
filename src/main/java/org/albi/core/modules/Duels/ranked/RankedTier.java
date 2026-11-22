package org.albi.core.modules.Duels.ranked;

import org.bukkit.ChatColor;

public enum RankedTier {
    BRONZE("Bronze", ChatColor.DARK_GRAY),
    SILVER("Silver", ChatColor.GRAY),
    GOLD("Gold", ChatColor.GOLD),
    PLATINUM("Platinum", ChatColor.AQUA),
    DIAMOND("Diamond", ChatColor.BLUE),
    MASTER("Master", ChatColor.DARK_PURPLE),
    GRANDMASTER("Grandmaster", ChatColor.RED),
    CHAMPION("Champion", ChatColor.DARK_RED);

    private final String name;
    private final ChatColor color;

    RankedTier(String name, ChatColor color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    /**
     * Get the tier and division based on Elo.
     * Each division is 100 Elo. 5 divisions per tier.
     * Total 500 Elo per tier.
     */
    public static String getTierDisplay(int elo) {
        if (elo < 0) elo = 0;
        int tierIndex = elo / 500;
        int division = 5 - ((elo % 500) / 100);

        if (tierIndex >= values().length) {
            return CHAMPION.color + "Champion 1";
        }

        RankedTier tier = values()[tierIndex];
        return tier.color + tier.name + " " + division;
    }

    public static RankedTier getTier(int elo) {
        int tierIndex = elo / 500;
        if (tierIndex >= values().length) return CHAMPION;
        if (tierIndex < 0) return BRONZE;
        return values()[tierIndex];
    }
}
