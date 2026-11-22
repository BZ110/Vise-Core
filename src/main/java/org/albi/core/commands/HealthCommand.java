package org.albi.core.commands;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.StatManagement;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HealthCommand extends BaseCommand {

    private final Core plugin;

    public HealthCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        if (args.length < 1) {
            plugin.getMessageAPI().sendMessage(player, ChatColor.RED + "Usage: /health <server>");
            return true;
        }

        String target = args[0].toLowerCase();
        String redisKey = "server:" + target + ":health:latest";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> data = StatManagement.getRedis().withJedis(j -> j.hgetAll(redisKey));

            if (data == null || data.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageAPI().sendMessage(player, ChatColor.RED + "Server data not found for " + target + ".")
                );
                return;
            }

            long now = System.currentTimeMillis();
            long ts = Long.parseLong(data.getOrDefault("timestamp_ms", "0"));
            long diff = now - ts;

            Bukkit.getScheduler().runTask(plugin, () -> {
                ChatColor g = ChatColor.GRAY, a = ChatColor.AQUA, b = ChatColor.BOLD;
                ChatColor green = ChatColor.GREEN, gold = ChatColor.GOLD, red = ChatColor.RED;

                plugin.getMessageAPI().sendMessage(player, b + "" + a + "✦ Server Health: " + target.toUpperCase());
                plugin.getMessageAPI().sendMessage(player, gold + "Status: " + (diff < 10_000 ? green + "Online" : red + "Offline"));
                plugin.getMessageAPI().sendMessage(player, gold + "Last Ping: " + g + ts + " (" + diff / 1000 + "s ago)");

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, b + "" + a + "» Players:");
                plugin.getMessageAPI().sendMessage(player, green + " • Online: " + g + data.getOrDefault("players_online", "?"));
                plugin.getMessageAPI().sendMessage(player, green + " • Max: " + g + data.getOrDefault("players_max", "?"));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, b + "" + a + "» Performance:");
                plugin.getMessageAPI().sendMessage(player, green + " • TPS: " + g + data.getOrDefault("tps_1m", "?"));
                plugin.getMessageAPI().sendMessage(player, green + " • Uptime: " + g + formatUptime(data.get("uptime_ms")));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, b + "" + a + "» Memory (MB):");
                plugin.getMessageAPI().sendMessage(player, green + " • Used: " + g + data.getOrDefault("mem_used_mb", "?"));
                plugin.getMessageAPI().sendMessage(player, green + " • Free: " + g + data.getOrDefault("mem_free_mb", "?"));
                plugin.getMessageAPI().sendMessage(player, green + " • Total: " + g + data.getOrDefault("mem_total_mb", "?"));
                plugin.getMessageAPI().sendMessage(player, green + " • Max: " + g + data.getOrDefault("mem_max_mb", "?"));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, b + "" + a + "» System:");
                plugin.getMessageAPI().sendMessage(player, green + " • OS: " + g + data.getOrDefault("system_os", "?"));
                plugin.getMessageAPI().sendMessage(player, green + " • Arch: " + g + data.getOrDefault("system_arch", "?"));
                plugin.getMessageAPI().sendMessage(player, green + " • Cores: " + g + data.getOrDefault("system_cores", "?"));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, b + "" + a + "» Redis Latency:");
                plugin.getMessageAPI().sendMessage(player, green + " • Ping: " + g + data.getOrDefault("redis_latency_ms", "?") + "ms");
            });
        });

        return true;
    }

    private String formatUptime(String uptimeMsStr) {
        if (uptimeMsStr == null) return "?";
        try {
            long ms = Long.parseLong(uptimeMsStr);
            long hours = TimeUnit.MILLISECONDS.toHours(ms);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
            return hours + "h " + minutes + "m";
        } catch (NumberFormatException e) {
            return "?";
        }
    }
}
