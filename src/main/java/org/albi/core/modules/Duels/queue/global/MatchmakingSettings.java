package org.albi.core.modules.Duels.queue.global;

import org.albi.core.Core;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

record MatchmakingSettings(String namespace, String server, String pool, boolean hostMatches,
                           int pollTicks, int scanSize, int offersPerPoll, int pendingLimit,
                           MatchmakingPolicy.Rules rules) {

    static MatchmakingSettings load(Core plugin) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("network.matchmaking");
        if (cfg == null || !cfg.getBoolean("enabled")) return null;

        try {
            String namespace = poolId(cfg.getString("namespace", "default"));
            String server = cfg.getString("server_id", "").strip();
            if (server.isEmpty()) server = plugin.getServerName().toLowerCase(Locale.ROOT);
            if (!server.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid proxy server_id");

            var defaults = cfg.getBoolean("use_default_pools", true)
                    ? DefaultMatchmakingPools.load(plugin.getResource("matchmaking/pools.yml"))
                    : DefaultMatchmakingPools.empty();
            Map<String, MatchmakingPolicy.Pool> pools = readPools(cfg.getConfigurationSection("pools"), defaults);
            String pool = selectPool(cfg.getString("pool", ""), plugin.getConfig().getString("region", "eu"),
                    server, pools.keySet(), defaults);
            if (!pools.containsKey(pool) && !defaults.pools().isEmpty()) {
                plugin.getLogger().warning("[Matchmaking] Pool '" + pool + "' has no routes. Configure it under network.matchmaking.pools.");
            }
            int pingStart = value(cfg, "ping.start_ms", 110, 1, 2000);
            int gapStart = value(cfg, "ping.gap_start_ms", 45, 0, 2000);
            var rules = new MatchmakingPolicy.Rules(pools,
                    value(cfg, "priority_step_seconds", 8, 1, 300),
                    value(cfg, "cross_region_after_seconds", 45, 0, 3600),
                    value(cfg, "unlisted_after_seconds", -1, -1, 3600),
                    value(cfg, "ping.same_region_add_ms", 25, 0, 1000),
                    value(cfg, "ping.cross_region_add_ms", 100, 0, 1000),
                    pingStart, value(cfg, "ping.max_ms", 260, pingStart, 3000),
                    gapStart, value(cfg, "ping.gap_max_ms", 130, gapStart, 3000),
                    value(cfg, "ping.relax_every_seconds", 5, 1, 300),
                    value(cfg, "ping.relax_by_ms", 15, 1, 500));
            return new MatchmakingSettings(namespace, server, pool, cfg.getBoolean("host_matches", true),
                    value(cfg, "poll_ticks", 10, 5, 40), value(cfg, "scan_size", 128, 16, 512),
                    value(cfg, "offers_per_poll", 12, 1, 32), value(cfg, "pending_per_host", 12, 1, 64), rules);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Matchmaking] " + e.getMessage() + ". Using local queues.");
            return null;
        }
    }

    static String selectPool(String explicit, String region, String server, Set<String> pools, DefaultMatchmakingPools defaults) {
        if (!explicit.isBlank()) return poolId(resolve(explicit, pools, defaults));
        if (defaults.pools().isEmpty()) return poolId(region);

        String home = resolve(region, pools, defaults);
        if (!pools.contains(home)) {
            String labeledRegion = defaults.fromLocationPrefix(region);
            if (labeledRegion != null) home = labeledRegion;
        }
        if (pools.contains(home) && !defaults.broad().contains(home)) return home;
        String inferred = defaults.fromLocationPrefix(server);
        if (inferred != null && (!defaults.broad().contains(inferred) || !pools.contains(home))) return inferred;
        return pools.contains(home) ? home : "global";
    }

    static Map<String, MatchmakingPolicy.Pool> readPools(ConfigurationSection section, DefaultMatchmakingPools defaults) {
        if (section == null) return defaults.pools();
        Map<String, MatchmakingPolicy.Pool> pools = new HashMap<>(defaults.pools());
        Set<String> configured = section.getKeys(false).stream().map(MatchmakingSettings::poolId).collect(Collectors.toSet());
        UnaryOperator<String> resolveRoute = destination -> poolId(resolve(destination, configured, defaults));
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            if (entry == null) throw new IllegalArgumentException("Invalid matchmaking pool " + name);
            String id = poolId(name);
            var legacy = defaults.legacyExamples() == null ? null : defaults.legacyExamples().getConfigurationSection(id);
            if (legacy != null && entry.getKeys(false).equals(legacy.getKeys(false))
                    && readPool(entry, id, null, MatchmakingSettings::poolId).equals(
                        readPool(legacy, id, null, MatchmakingSettings::poolId))) continue;
            pools.put(id, readPool(entry, id, defaults.pools().get(id), resolveRoute));
        }
        return Map.copyOf(pools);
    }

    private static MatchmakingPolicy.Pool readPool(ConfigurationSection entry, String id, MatchmakingPolicy.Pool base,
                                                    UnaryOperator<String> resolveRoute) {
        if (base == null) base = new MatchmakingPolicy.Pool(id.split("-", 2)[0], List.of(), Map.of());
        boolean explicitPriority = entry.contains("priority");
        var priority = explicitPriority ? entry.getStringList("priority").stream().map(resolveRoute)
                .filter(destination -> !id.equals(destination)).distinct().limit(64).toList() : base.priority();
        Map<String, Integer> latency = new HashMap<>(base.latency());
        ConfigurationSection estimates = entry.getConfigurationSection("latency_ms");
        if (estimates != null) {
            for (String destination : estimates.getKeys(false)) {
                latency.put(resolveRoute.apply(destination), value(estimates, destination, 0, 0, 1000));
            }
        }
        return new MatchmakingPolicy.Pool(poolId(entry.getString("region", base.region())), priority,
                Map.copyOf(latency), explicitPriority ? Map.of() : base.steps());
    }

    private static String resolve(String name, Set<String> configured, DefaultMatchmakingPools defaults) {
        String id = name.strip().toLowerCase(Locale.ROOT);
        return configured.contains(id) ? id : defaults.resolve(id);
    }

    private static String poolId(String text) {
        String id = text.strip().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid pool or namespace identifier");
        return id;
    }

    private static int value(ConfigurationSection cfg, String path, int fallback, int min, int max) {
        return Math.clamp(cfg.getInt(path, fallback), min, max);
    }
}
