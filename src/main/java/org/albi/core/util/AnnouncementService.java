package org.albi.core.util;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementService {

    private final Core plugin;

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
        String raw = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        String msg = plugin.getMessageAPI().toColor(raw);
        String lobbyWorld = plugin.getConfig().getString("lobby_world", "world");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().getName().equalsIgnoreCase(lobbyWorld)) continue;
            ExtendedPlayer ep = plugin.getExtendedPlayer(player);
            if (ep == null) continue;
            if (ep.getPlayerDuelInstance() != null) continue;
            if (ep.isLavaRisingEvent()) continue;
            player.sendMessage(" ");
            player.sendMessage(plugin.getMessageAPI().toColor("&8&m----------------------------------------"));
            player.sendMessage(msg);
            player.sendMessage(plugin.getMessageAPI().toColor("&8&m----------------------------------------"));
            player.sendMessage(" ");
        }
    }
}
