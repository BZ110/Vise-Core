package org.albi.core.managers.tiers;

import org.albi.core.Core;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class TierManager {

    private enum Provider {
        PVPTIERS("pvptiers", "https://pvptiers.com/api/v1/profile/", PvPTiersProfile::new),
        MCTIERS("mctiers", "https://mctiers.com/api/v2/profile/", MCTiersProfile::new),
        SUBTIERS("subtiers", "https://subtiers.net/api/v1/profile/", SubtiersProfile::new);

        private final String key;
        private final String endpoint;
        private final Supplier<SuperProfile> createProfile;

        Provider(String key, String endpoint, Supplier<SuperProfile> createProfile) {
            this.key = key;
            this.endpoint = endpoint;
            this.createProfile = createProfile;
        }
    }

    private record CachedProfiles(long fetchedAt, Map<Class<? extends SuperProfile>, SuperProfile> profiles) {}

    private static final long CACHE_DURATION = Duration.ofHours(6).toMillis();

    private final Logger logger;
    private final List<Provider> providers;
    private final Function<HttpRequest, CompletableFuture<HttpResponse<String>>> send;
    private final Map<UUID, CachedProfiles> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Map<Class<? extends SuperProfile>, SuperProfile>>> inFlight = new ConcurrentHashMap<>();

    public TierManager(Core plugin) {
        this(plugin.getConfig(), plugin.getLogger(), httpTransport());
    }

    TierManager(ConfigurationSection config, Logger logger,
                Function<HttpRequest, CompletableFuture<HttpResponse<String>>> send) {
        this.logger = logger;
        this.send = send;
        providers = config.getBoolean("tier_profiles.enabled", true)
                ? Arrays.stream(Provider.values())
                        .filter(provider -> config.getBoolean("tier_profiles.providers." + provider.key, true))
                        .toList()
                : List.of();
    }

    public CompletableFuture<Map<Class<? extends SuperProfile>, SuperProfile>> fetchAll(UUID uuid, boolean force) {
        if (providers.isEmpty()) return CompletableFuture.completedFuture(Map.of());

        CachedProfiles cached = profileCache.get(uuid);
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt() < CACHE_DURATION) {
            return CompletableFuture.completedFuture(cached.profiles());
        }

        var task = inFlight.computeIfAbsent(uuid, this::fetchEnabled);
        // Register after publishing the task so even an already-completed response is removed.
        task.whenComplete((profiles, error) -> inFlight.remove(uuid, task));
        return task;
    }

    private CompletableFuture<Map<Class<? extends SuperProfile>, SuperProfile>> fetchEnabled(UUID uuid) {
        long now = System.currentTimeMillis();
        var requests = providers.stream().map(provider -> fetch(uuid, provider)).toList();
        return CompletableFuture.allOf(requests.toArray(CompletableFuture<?>[]::new)).thenApply(ignored -> {
            Map<Class<? extends SuperProfile>, SuperProfile> results = new HashMap<>();
            for (var request : requests) {
                SuperProfile profile = request.join();
                results.put(profile.getClass(), profile);
            }
            var profiles = Map.copyOf(results);
            profileCache.put(uuid, new CachedProfiles(now, profiles));
            return profiles;
        });
    }

    private static Function<HttpRequest, CompletableFuture<HttpResponse<String>>> httpTransport() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private CompletableFuture<SuperProfile> fetch(UUID uuid, Provider provider) {
        SuperProfile profile = provider.createProfile.get();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(provider.endpoint + uuid))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return send.apply(request)
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        profile.parseJson(response.body());
                    } else if (response.statusCode() == 404) {
                        profile.status = Status.NOT_EXISTING;
                    } else {
                        profile.status = Status.API_ISSUE;
                    }
                    return profile;
                })
                .exceptionally(ex -> {
                    logger.warning("Failed to fetch tier data from " + provider.endpoint + ": " + ex.getMessage());
                    profile.status = Status.API_ISSUE;
                    return profile;
                });
    }
}
