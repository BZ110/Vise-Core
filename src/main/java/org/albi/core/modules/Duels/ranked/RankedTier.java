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
     * Division size and count come from the server's rating settings.
     */
    public static String getTierDisplay(int elo) {
        if (elo < 0) elo = 0;
        RankedRules rules = RankedRules.current();
        int tierIndex = elo / rules.pointsPerTier();
        int division = rules.divisionsPerTier() - (elo % rules.pointsPerTier()) / rules.pointsPerDivision();

        if (tierIndex >= values().length) {
            return CHAMPION.color + "Champion 1";
        }

        RankedTier tier = values()[tierIndex];
        return tier.color + tier.name + " " + division;
    }

    public static RankedTier getTier(int elo) {
        int tierIndex = Math.max(0, elo) / RankedRules.current().pointsPerTier();
        if (tierIndex >= values().length) return CHAMPION;
        return values()[tierIndex];
    }
}
