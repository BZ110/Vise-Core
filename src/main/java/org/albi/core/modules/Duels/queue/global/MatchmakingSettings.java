package org.albi.core.modules.Duels.queue.global;

import org.albi.core.Core;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

            String pool = cfg.getString("pool", "").strip();
            if (pool.isEmpty()) pool = plugin.getConfig().getString("region", "eu");
            Map<String, MatchmakingPolicy.Pool> pools = readPools(cfg.getConfigurationSection("pools"));
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
            return new MatchmakingSettings(namespace, server, poolId(pool), cfg.getBoolean("host_matches", true),
                    value(cfg, "poll_ticks", 10, 5, 40), value(cfg, "scan_size", 128, 16, 512),
                    value(cfg, "offers_per_poll", 12, 1, 32), value(cfg, "pending_per_host", 12, 1, 64), rules);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Matchmaking] " + e.getMessage() + ". Using local queues.");
            return null;
        }
    }

    private static Map<String, MatchmakingPolicy.Pool> readPools(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, MatchmakingPolicy.Pool> pools = new HashMap<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            if (entry == null) throw new IllegalArgumentException("Invalid matchmaking pool " + name);
            String id = poolId(name);
            var priority = entry.getStringList("priority").stream().map(MatchmakingSettings::poolId)
                    .filter(destination -> !id.equals(destination)).distinct().limit(64).toList();
            Map<String, Integer> latency = new HashMap<>();
            ConfigurationSection estimates = entry.getConfigurationSection("latency_ms");
            if (estimates != null) {
                for (String destination : estimates.getKeys(false)) {
                    latency.put(poolId(destination), value(estimates, destination, 0, 0, 1000));
                }
            }
            String region = poolId(entry.getString("region", id.split("-", 2)[0]));
            pools.put(id, new MatchmakingPolicy.Pool(region, priority, Map.copyOf(latency)));
        }
        return Map.copyOf(pools);
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
