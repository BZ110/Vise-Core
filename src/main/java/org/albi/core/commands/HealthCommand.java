package org.albi.core.commands;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.util.ConfigValues;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;
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
            plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.usage-health-server", ChatColor.RED));
            return true;
        }

        String target = args[0].toLowerCase(Locale.ROOT);
        var shared = StatManagement.getSharedState();
        var redis = StatManagement.getRedis();
        long offlineThreshold = ConfigValues.seconds(plugin.getConfig(), "network.travel.offline_after_seconds", 10, 1, 3600);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var data = shared.readHealth(target);

            if (data == null || data.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.server-data-not-found-for", ChatColor.RED, target))
                );
                return;
            }

            long now = System.currentTimeMillis();
            long ts = Long.parseLong(data.getOrDefault("timestamp_ms", "0"));
            long diff = now - ts;

            Bukkit.getScheduler().runTask(plugin, () -> {
                ChatColor g = ChatColor.GRAY, a = ChatColor.AQUA, b = ChatColor.BOLD;
                ChatColor green = ChatColor.GREEN, gold = ChatColor.GOLD, red = ChatColor.RED;

                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.server-health", b, a, target.toUpperCase(Locale.ROOT)));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.status", gold, (diff < offlineThreshold ? green + "Online" : red + "Offline")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.last-ping-s-ago", gold, g, ts, diff / 1000));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.players", b, a));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.online", green, g, data.getOrDefault("players_online", "?")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.max", green, g, data.getOrDefault("players_max", "?")));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.performance", b, a));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.tps", green, g, data.getOrDefault("tps_1m", "?")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.uptime", green, g, formatUptime(data.get("uptime_ms"))));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.memory-mb", b, a));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.used", green, g, data.getOrDefault("mem_used_mb", "?")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.free", green, g, data.getOrDefault("mem_free_mb", "?")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.total", green, g, data.getOrDefault("mem_total_mb", "?")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.max", green, g, data.getOrDefault("mem_max_mb", "?")));

                plugin.getMessageAPI().sendMessage(player, "");
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.system", b, a));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.os", green, g, data.getOrDefault("system_os", "?")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.arch", green, g, data.getOrDefault("system_arch", "?")));
                plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.cores", green, g, data.getOrDefault("system_cores", "?")));

                if (redis.isHealthy()) {
                    plugin.getMessageAPI().sendMessage(player, "");
                    plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.redis-latency", b, a));
                    plugin.getMessageAPI().sendMessage(player, Messages.text(player, "messages.health-command.ping-ms", green, g, data.getOrDefault("redis_latency_ms", "?")));
                }
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
