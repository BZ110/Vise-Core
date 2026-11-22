package org.albi.core.modules.Duels.queue.global;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.albi.core.modules.Duels.queue.global.MatchmakingPolicyTest.NOW;
import static org.albi.core.modules.Duels.queue.global.MatchmakingPolicyTest.ticket;
import static org.junit.jupiter.api.Assertions.*;

class DefaultMatchmakingPoolsTest {
    private final DefaultMatchmakingPools defaults = DefaultMatchmakingPools.load(
            getClass().getResourceAsStream("/matchmaking/pools.yml"));

    @Test
    void givesEveryLocationRoutesAndDistinctTimingTiersAcrossTheCatalog() {
        var pools = defaults.pools();
        assertTrue(pools.keySet().containsAll(Set.of("eu", "na-central-america", "na-caribbean", "sa",
                "af-central", "af-north", "af-east", "af-south", "af-west", "middle-east", "asia-central",
                "asia-east", "asia-south", "asia-southeast", "oc-new-zealand", "oc-melanesia", "oc-micronesia",
                "oc-polynesia", "antarctica", "global")));
        pools.forEach((origin, pool) -> {
            var destinations = new HashSet<>(pools.keySet());
            destinations.remove(origin);
            assertEquals(destinations, new HashSet<>(pool.priority()), origin);
            assertEquals(destinations.size(), pool.priority().size(), origin);
            assertEquals(destinations, pool.steps().keySet(), origin);
            assertTrue(pool.steps().values().stream().allMatch(step -> step > 0));
            pool.latency().forEach((destination, estimate) ->
                    assertEquals(estimate, pools.get(destination).latency().get(origin)));
        });
        assertTrue(pools.get("eu-east").steps().get("eu-central") < pools.get("eu-east").steps().get("oc-east"));
    }

    @Test
    void recognizesExistingRegionAndProxyNamesWithoutAPoolSetting() {
        assertEquals("na-west", select("", "NA", "us-west-2"));
        assertEquals("oc-east", select("", "EU", "sydney-01"));
        assertEquals("asia-east", select("", "EU", "asia-east1-duels"));
        assertEquals("asia-south", select("", "asia-south-1", "practice-42"));
        assertEquals("af-north", select("", "Northern Africa", "us-west-2"));
        assertEquals("na", select("", "North America", "na-1"));
        assertEquals("global", select("", "unset", "lobby-01"));
        assertEquals("eu-east", select("eu_east", "NA", "us-west-2"));
        assertEquals("custom-pool", select("custom-pool", "EU", "eu-east-1"));
    }

    @Test
    void upgradesUntouchedLegacyExamplesAndKeepsExplicitOverrides() throws Exception {
        assertEquals(defaults.pools(), MatchmakingSettings.readPools(defaults.legacyExamples(), defaults));

        var overrides = new YamlConfiguration();
        overrides.loadFromString("""
                eu-east:
                  priority: [us-west, eu-central]
                eu-west:
                  latency_ms: {new-york: 81}
                eu-north:
                  priority: []
                private-test:
                  region: eu
                  priority: [eu-east]
                """);
        var pools = MatchmakingSettings.readPools(overrides, defaults);
        assertEquals(List.of("na-west", "eu-central"), pools.get("eu-east").priority());
        assertTrue(pools.get("eu-east").steps().isEmpty());
        assertEquals(defaults.pools().get("eu-west").steps(), pools.get("eu-west").steps());
        assertEquals(81, pools.get("eu-west").latency().get("na-east"));
        assertTrue(pools.get("eu-north").priority().isEmpty());
        assertEquals("private-test", MatchmakingSettings.selectPool("", "private-test", "eu-east-1", pools.keySet(), defaults));

        var noDefaults = DefaultMatchmakingPools.empty();
        var legacyOnly = MatchmakingSettings.readPools(overrides, noDefaults);
        assertFalse(legacyOnly.containsKey("global"));
        assertEquals(List.of("us-west", "eu-central"), legacyOnly.get("eu-east").priority());
    }

    @Test
    void keepsMatchesInRegionAndUsesACrossRegionHostAfterTheWaitGate() {
        var policy = policy(-1);
        var frankfurt = host("eu-central");
        var newYork = host("na-east");
        var east = ticket("eu-east", "Axe", false, 60, 20, 0, false);
        var west = ticket("eu-west", "Axe", false, 60, 20, 0, false);
        assertEquals(frankfurt, policy.best(east, List.of(west), List.of(newYork, frankfurt), NOW, false).host());

        var early = ticket("eu-west", "Axe", false, 44, 20, 0, false);
        assertNull(policy.best(early, List.of(west), List.of(newYork), NOW, false));
        var ready = ticket("eu-west", "Axe", false, 45, 20, 0, false);
        assertEquals(newYork, policy.best(ready, List.of(west), List.of(newYork), NOW, false).host());
    }

    @Test
    void honorsTheConfiguredUnlistedWaitIndependentlyOfCatalogLength() {
        var policy = policy(70);
        var custom = host("custom");
        var first = ticket("eu-east", "Axe", false, 120, 20, 0, false);
        var early = ticket("eu-east", "Axe", false, 69, 20, 0, false);
        var ready = ticket("eu-east", "Axe", false, 70, 20, 0, false);
        assertNull(policy.best(first, List.of(early), List.of(custom), NOW, false));
        assertEquals(custom, policy.best(first, List.of(ready), List.of(custom), NOW, false).host());
    }

    @Test
    void anUnlabeledBackendMatchesLocallyImmediatelyAndTreatsTransfersConservatively() {
        var policy = policy(-1);
        var local = new MatchmakingPolicy.Host("origin-global", "session", "global", Set.of("Axe"), Set.of("Axe"), 0, 100, 1);
        var remote = host("global");
        var first = ticket("global", "Axe", false, 60, 20, 0, false);
        var justJoined = ticket("global", "Axe", false, 0, 20, 0, false);
        assertEquals(local, policy.best(first, List.of(justJoined), List.of(remote, local), NOW, false).host());
        assertNull(policy.best(first, List.of(justJoined), List.of(remote), NOW, false));

        var ready = ticket("global", "Axe", false, 45, 20, 0, false);
        var highPing = ticket("global", "Axe", false, 60, 175, 0, false);
        assertEquals(remote, policy.best(first, List.of(ready), List.of(remote), NOW, false).host());
        assertNull(policy.best(highPing, List.of(ready), List.of(remote), NOW, false));
    }

    private String select(String pool, String region, String server) {
        return MatchmakingSettings.selectPool(pool, region, server, defaults.pools().keySet(), defaults);
    }

    private MatchmakingPolicy policy(int unlisted) {
        return new MatchmakingPolicy(new MatchmakingPolicy.Rules(defaults.pools(), 8, 45, unlisted, 25, 100,
                110, 260, 45, 130, 5, 15));
    }

    private static MatchmakingPolicy.Host host(String pool) {
        return new MatchmakingPolicy.Host("host-" + pool, "session", pool, Set.of("Axe"), Set.of("Axe"), 0, 100, 1);
    }
}
