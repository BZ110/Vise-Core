package org.albi.core.util;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementService {

    private final Core plugin;
    private int nextMessage;

    public AnnouncementService(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("announcements.enabled", true)) return;
        int interval = Math.max(30, plugin.getConfig().getInt("announcements.interval_seconds", 180));
        new BukkitRunnable() {
            @Override
            public void run() {
                sendOne();
            }
        }.runTaskTimer(plugin, interval * 20L, interval * 20L);
    }

    private void sendOne() {
        List<String> messages = plugin.getConfig().getStringList("announcements.messages");
        if (messages == null || messages.isEmpty()) return;
        var config = plugin.getConfig();
        int index = config.getBoolean("announcements.shuffle", true) ? ThreadLocalRandom.current().nextInt(messages.size())
                : Math.floorMod(nextMessage++, messages.size());
        String msg = plugin.getMessageAPI().toColor(messages.get(index));
        List<String> worlds = config.getStringList("announcements.worlds");
        boolean excludeDuels = config.getBoolean("announcements.exclude_duels", true);
        boolean excludeLava = config.getBoolean("announcements.exclude_lava_rising", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean targetWorld = worlds.isEmpty() ? plugin.isLobby(player) : worlds.stream()
                    .anyMatch(world -> world.equals("*") || world.equalsIgnoreCase(player.getWorld().getName()));
            if (!targetWorld) continue;
            ExtendedPlayer ep = plugin.getExtendedPlayer(player);
            if (ep == null) continue;
            if (excludeDuels && ep.getPlayerDuelInstance() != null) continue;
            if (excludeLava && ep.isLavaRisingEvent()) continue;
            Messages.send(player, "messages.announcement-service.send-one");
            Messages.send(player, "messages.announcement-service.divider");
            player.sendMessage(msg);
            Messages.send(player, "messages.announcement-service.divider");
            Messages.send(player, "messages.announcement-service.send-one");
        }
    }
}
