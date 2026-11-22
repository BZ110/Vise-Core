package org.albi.core.managers.tiers;

import org.albi.core.Core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TierManager {

    private final Core plugin;
    private final HttpClient httpClient;
    private final Map<UUID, Map<Class<? extends SuperProfile>, SuperProfile>> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFetch = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Map<Class<? extends SuperProfile>, SuperProfile>>> inFlight = new ConcurrentHashMap<>();
    
    private static final long CACHE_DURATION = 6 * 60 * 60 * 1000L; // 6 Hours

    private static final String MCTIERS_API = "https://mctiers.com/api/v2/profile/";
    private static final String PVPTIERS_API = "https://pvptiers.com/api/v1/profile/";
    private static final String SUBTIERS_API = "https://subtiers.net/api/v1/profile/";

    public TierManager(Core plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public CompletableFuture<Map<Class<? extends SuperProfile>, SuperProfile>> fetchAll(UUID uuid, boolean force) {
        long now = System.currentTimeMillis();
        
        // Return from cache if valid and not forced
        if (!force && profileCache.containsKey(uuid) && now - lastFetch.getOrDefault(uuid, 0L) < CACHE_DURATION) {
            return CompletableFuture.completedFuture(profileCache.get(uuid));
        }

        // Return current in-flight request if any
        if (inFlight.containsKey(uuid)) {
            return inFlight.get(uuid);
        }

        CompletableFuture<SuperProfile> pvp = fetch(uuid, PVPTIERS_API, new PvPTiersProfile());
        CompletableFuture<SuperProfile> mc = fetch(uuid, MCTIERS_API, new MCTiersProfile());
        CompletableFuture<SuperProfile> sub = fetch(uuid, SUBTIERS_API, new SubtiersProfile());

        CompletableFuture<Map<Class<? extends SuperProfile>, SuperProfile>> task = CompletableFuture.allOf(pvp, mc, sub).thenApply(v -> {
            Map<Class<? extends SuperProfile>, SuperProfile> results = new HashMap<>();
            results.put(PvPTiersProfile.class, pvp.join());
            results.put(MCTiersProfile.class, mc.join());
            results.put(SubtiersProfile.class, sub.join());
            
            profileCache.put(uuid, results);
            lastFetch.put(uuid, now);
            inFlight.remove(uuid); // Task finished
            return results;
        });

        inFlight.put(uuid, task);
        return task;
    }

    private CompletableFuture<SuperProfile> fetch(UUID uuid, String baseUrl, SuperProfile profile) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + uuid.toString()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)") // Standard UA to avoid blocks
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
                    plugin.getLogger().warning("Failed to fetch tier data from " + baseUrl + ": " + ex.getMessage());
                    profile.status = Status.API_ISSUE;
                    return profile;
                });
    }
}
