package org.albi.core.data;

import org.albi.core.util.ConfigValues;

import org.albi.core.Core;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class StatRecorder {

    private final Core plugin;
    private final SharedState shared;
    private final String serverName;

    private final Runtime runtime = Runtime.getRuntime();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();



    public StatRecorder(Core plugin, SharedState shared, String server) {
        this.plugin = plugin;
        this.shared = shared;
        this.serverName = server.toLowerCase(Locale.ROOT);
    }

    public void start() {
        var config = plugin.getConfig();
        if (!shared.isEnabled() || !config.getBoolean("network.health.enabled", true)) return;
        int sampleTicks = ConfigValues.integer(config, "network.health.sample_interval_ticks", 100, 20, 12_000);
        int maxSamples = ConfigValues.integer(config, "network.health.max_samples", 17_280, 1, 1_000_000);
        int offlineAfter = ConfigValues.integer(config, "network.travel.offline_after_seconds", 10, 1, 3600);
        int ttl = Math.max(60, Math.max((sampleTicks + 19) / 20, offlineAfter) * 3);
        boolean includeWorlds = config.getBoolean("network.health.include_worlds", true);
        AtomicBoolean publishing = new AtomicBoolean();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (publishing.get()) return;

                final long ts = System.currentTimeMillis();
                final String iso = Instant.ofEpochMilli(ts).toString();

                final Map<String, String> data = new HashMap<>();

                data.put("timestamp_ms", Long.toString(ts));
                data.put("timestamp_iso", iso);
                data.put("server", serverName);

                data.put("players_online", Integer.toString(Bukkit.getOnlinePlayers().size()));
                data.put("players_max", Integer.toString(Bukkit.getMaxPlayers()));

                double[] tps = Bukkit.getServer().getTPS();
                data.put("tps_1m", Double.toString(tps[0]));
                data.put("tps_5m", Double.toString(tps[1]));
                data.put("tps_15m", Double.toString(tps[2]));

                for (World world : includeWorlds ? Bukkit.getWorlds() : List.<World>of()) {
                    String key = "world_" + normalizeSegment(world.getName());
                    data.put(key + "_chunks", Integer.toString(world.getLoadedChunks().length));
                    data.put(key + "_players", Integer.toString(world.getPlayers().size()));
                }

                publishing.set(true);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        enrichWithJvmAndServerData(data);
                        shared.recordHealth(serverName, data, maxSamples, ttl);
                    } finally {
                        publishing.set(false);
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, sampleTicks);
    }

    private void enrichWithJvmAndServerData(Map<String, String> data) {

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        data.put("mem_used_mb", Long.toString(usedMemory / 1_048_576L));
        data.put("mem_free_mb", Long.toString(freeMemory / 1_048_576L));
        data.put("mem_total_mb", Long.toString(totalMemory / 1_048_576L));
        data.put("mem_max_mb", Long.toString(maxMemory / 1_048_576L));

        data.put("system_cores", Integer.toString(runtime.availableProcessors()));
        data.put("system_os", osBean.getName() + " " + osBean.getVersion());
        data.put("system_arch", osBean.getArch());
        data.put("uptime_ms", Long.toString(rtBean.getUptime()));

        data.put("threads_live", Integer.toString(threadBean.getThreadCount()));
        data.put("threads_peak", Integer.toString(threadBean.getPeakThreadCount()));

        for (GarbageCollectorMXBean gc : gcBeans) {
            String key = "gc_" + normalizeSegment(gc.getName());
            data.put(key + "_collections", Long.toString(gc.getCollectionCount()));
            data.put(key + "_time_ms", Long.toString(gc.getCollectionTime()));
        }

        data.put("server_version", Bukkit.getVersion());
        data.put("server_bukkit", Bukkit.getBukkitVersion());
        data.put("server_motd", Bukkit.getMotd());
    }

    private static String normalizeSegment(String input) {
        return input.toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
