package org.albi.core.data;

import org.albi.core.Core;
import org.albi.core.data.StatManagement.RedisWrapper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.XAddParams;

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

public class StatRecorder {

    private final Core plugin;
    private final RedisWrapper redisWrapper;
    private final String serverName;

    private final Runtime runtime = Runtime.getRuntime();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    private static final int MAX_SAMPLES = 17_280;

    public StatRecorder(Core plugin, RedisWrapper wrapper, String server) {
        this.plugin = plugin;
        this.redisWrapper = wrapper;
        this.serverName = server.toLowerCase(Locale.ROOT);
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {

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

                for (World world : Bukkit.getWorlds()) {
                    String key = "world_" + normalizeSegment(world.getName());
                    data.put(key + "_chunks", Integer.toString(world.getLoadedChunks().length));
                    data.put(key + "_players", Integer.toString(world.getPlayers().size()));
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    enrichWithJvmAndServerData(data);

                    redisWrapper.withJedis(jedis -> {
                        String keyPrefix = "server:" + serverName + ":health";
                        String streamKey = keyPrefix + ":stream";
                        String latestKey = keyPrefix + ":latest";

                        Pipeline p = jedis.pipelined();

                        p.hmset(latestKey, data);

                        p.xadd(
                                streamKey,
                                XAddParams.xAddParams().maxLen(MAX_SAMPLES).approximateTrimming(),
                                data
                        );

                        p.sync();
                        return null;
                    });
                });
            }
        }.runTaskTimer(plugin, 0L, 100L);
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