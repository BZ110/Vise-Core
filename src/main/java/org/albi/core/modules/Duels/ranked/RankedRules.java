package org.albi.core.modules.Duels.ranked;

import org.bukkit.configuration.ConfigurationSection;

import static org.albi.core.util.ConfigValues.integer;

/** Rating policy shared by both queue backends, rating updates, and the tier display. */
public record RankedRules(int startingElo, int placementMatches, int regularK, int placementK, int divisor,
                          int pointsPerDivision, int divisionsPerTier, int initialRange, int widening,
                          int widenEverySeconds, int maxRange) {

    private static volatile RankedRules current = new RankedRules(1000, 5, 32, 64, 400, 100, 5, 50, 25, 3, 0);

    public static RankedRules current() {
        return current;
    }

    public static void configure(ConfigurationSection config) {
        current = from(config);
        RankedStats.PLACEMENT_THRESHOLD = current.placementMatches;
    }

    public static RankedRules from(ConfigurationSection config) {
        int initialRange = integer(config, "ranked.matchmaking.initial_elo_range", 50, 0, 100_000);
        int maxRange = integer(config, "ranked.matchmaking.max_elo_range", 0, 0, 1_000_000);
        return new RankedRules(
                integer(config, "ranked.starting_elo", 1000, 0, 100_000),
                integer(config, "ranked.placement_matches", 5, 0, 1000),
                integer(config, "ranked.rating.regular_k", 32, 1, 1000),
                integer(config, "ranked.rating.placement_k", 64, 1, 1000),
                integer(config, "ranked.rating.expected_score_divisor", 400, 1, 10_000),
                integer(config, "ranked.tiers.points_per_division", 100, 1, 10_000),
                integer(config, "ranked.tiers.divisions_per_tier", 5, 1, 20),
                initialRange, integer(config, "ranked.matchmaking.elo_range_increment", 25, 0, 100_000),
                integer(config, "ranked.matchmaking.relax_every_seconds", 3, 1, 3600),
                maxRange == 0 ? 0 : Math.max(initialRange, maxRange));
    }

    public int searchRange(int waitSeconds) {
        long expanded = initialRange + (long) (Math.max(0, waitSeconds) / widenEverySeconds) * widening;
        return (int) Math.min(maxRange == 0 ? Integer.MAX_VALUE : maxRange, expanded);
    }

    int pointsPerTier() {
        return pointsPerDivision * divisionsPerTier;
    }
}
