package org.albi.core.util;

import org.albi.core.Core;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

public final class AutoRestartService {

    private final Core plugin;
    private int remainingSeconds;
    private BukkitTask task;
    private final Set<Integer> warningThresholds = new HashSet<>();

    public AutoRestartService(Core plugin) {
        this.plugin = plugin;
        setupThresholds();
    }

    private void setupThresholds() {
        // 1 hour, 30m, 15m, 10m, 5m
        warningThresholds.add(3600);
        warningThresholds.add(1800);
        warningThresholds.add(900);
        warningThresholds.add(600);
        warningThresholds.add(300);
        
        // 1m, 30s, 15s, 10s
        warningThresholds.add(60);
        warningThresholds.add(30);
        warningThresholds.add(15);
        warningThresholds.add(10);
        
        // 5, 4, 3, 2, 1
        warningThresholds.add(5);
        warningThresholds.add(4);
        warningThresholds.add(3);
        warningThresholds.add(2);
        warningThresholds.add(1);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("autorestart.enabled", true)) return;

        int intervalHours = plugin.getConfig().getInt("autorestart.interval_hours", 24);
        this.remainingSeconds = intervalHours * 3600;

        resume();
    }

    public void resume() {
        if (task != null) task.cancel();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (remainingSeconds <= 0) {
                    executeRestart();
                    cancel();
                    return;
                }

                if (warningThresholds.contains(remainingSeconds)) {
                    broadcastWarning(remainingSeconds);
                }

                remainingSeconds--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void pause() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void setRemainingSeconds(int seconds) {
        this.remainingSeconds = seconds;
        if (task == null) resume();
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void cancelRestart() {
        pause();
        remainingSeconds = -1;
    }

    private void broadcastWarning(int seconds) {
        String timeStr = formatTime(seconds);
        String msg = plugin.getMessageAPI().toColorLang("autorestart_warning", new String[]{timeStr});
        Bukkit.broadcastMessage(msg);
    }

    private void executeRestart() {
        Bukkit.getLogger().info("[Core] AutoRestart triggering shutdown...");
        Bukkit.shutdown();
    }

    public String formatTime(int totalSeconds) {
        if (totalSeconds < 0) return "N/A";
        
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || (hours == 0 && minutes == 0)) sb.append(seconds).append("s");
        
        return sb.toString().trim();
    }
}
