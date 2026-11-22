package org.albi.core.modules.Duels.ranked;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankedRulesTest {
    @Test
    void configuredEloFactorsAndSearchCapApplyWithTheSameRules() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
                ranked:
                  starting_elo: 1600
                  placement_matches: 0
                  rating:
                    regular_k: 20
                    placement_k: 50
                  tiers:
                    points_per_division: 150
                    divisions_per_tier: 4
                  matchmaking:
                    initial_elo_range: 75
                    elo_range_increment: 10
                    relax_every_seconds: 4
                    max_elo_range: 100
                """);

        var rules = RankedRules.from(config);
        assertEquals(1600, rules.startingElo());
        assertEquals(0, rules.placementMatches());
        assertEquals(600, rules.pointsPerTier());
        assertEquals(1610, EloCalculator.calculate(1600, 1600, true, false, rules));
        assertEquals(1625, EloCalculator.calculate(1600, 1600, true, true, rules));
        assertEquals(75, rules.searchRange(3));
        assertEquals(85, rules.searchRange(4));
        assertEquals(100, rules.searchRange(90));
    }
}
