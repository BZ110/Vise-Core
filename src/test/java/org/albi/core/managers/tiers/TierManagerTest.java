package org.albi.core.managers.tiers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ConnectException;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TierManagerTest {
    private static final Set<Class<? extends SuperProfile>> ALL_PROFILES =
            Set.of(MCTiersProfile.class, PvPTiersProfile.class, SubtiersProfile.class);
    private static final Set<String> ALL_HOSTS = Set.of("mctiers.com", "pvptiers.com", "subtiers.net");

    private final UUID player = UUID.randomUUID();
    private final List<HttpRequest> requests = new ArrayList<>();
    private final YamlConfiguration config = new YamlConfiguration();

    @ParameterizedTest
    @CsvSource({"mctiers, mctiers.com, MCTiersProfile", "pvptiers, pvptiers.com, PvPTiersProfile",
            "subtiers, subtiers.net, SubtiersProfile"})
    void providerSwitchSuppressesBothRequestsAndProfileEntries(String key, String host, String type) {
        config.set("tier_profiles.providers." + key, false);
        var tiers = manager();

        for (boolean force : List.of(false, true)) {
            var profiles = tiers.fetchAll(player, force).join();
            assertEquals(2, profiles.size());
            assertFalse(profiles.keySet().stream().anyMatch(profile -> profile.getSimpleName().equals(type)));
        }
        assertEquals(4, requests.size());
        assertFalse(requests.stream().anyMatch(request -> request.uri().getHost().equals(host)));
    }

    @Test
    void masterSwitchOverridesEnabledProviders() {
        config.set("tier_profiles.enabled", false);
        config.set("tier_profiles.providers.mctiers", true);
        var tiers = manager();

        assertTrue(tiers.fetchAll(player, true).join().isEmpty());
        assertTrue(tiers.fetchAll(player, false).join().isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void missingSettingsPreserveAllIntegrationsAndForcedRefreshBypassesCache() {
        var tiers = manager();

        var profiles = tiers.fetchAll(player, true).join();
        assertEquals(ALL_PROFILES, profiles.keySet());
        assertSame(profiles, tiers.fetchAll(player, false).join());
        assertEquals(3, requests.size());
        assertEquals(ALL_HOSTS, Set.copyOf(requests.stream().map(request -> request.uri().getHost()).toList()));

        assertEquals(ALL_PROFILES, tiers.fetchAll(player, true).join().keySet());
        assertEquals(6, requests.size());
    }

    private TierManager manager() {
        var logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new TierManager(config, logger, request -> {
            requests.add(request);
            return CompletableFuture.failedFuture(new ConnectException("Test transport is offline"));
        });
    }
}
