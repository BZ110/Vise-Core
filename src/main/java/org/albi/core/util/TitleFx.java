package org.albi.core.util;

import org.albi.core.Core;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public final class TitleFx {

    private static final String BASE = hex("#f2a7df") + "§l";
    private static final String TRAIL = hex("#f7bde8") + "§l";
    private static final String FOCUS = hex("#ff84d6") + "§l";

    private TitleFx() {}

    public static void playFight(Core plugin, Player player, String subtitle) {
        if (plugin == null || player == null || !player.isOnline()) return;
        playSwish(plugin, player, "Fight!", subtitle, 2L, 6, 8);
    }

    public static void playSwish(Core plugin, Player player, String text, String subtitle, long intervalTicks, int stayTicks, int loops) {
        if (plugin == null || player == null || text == null || text.isEmpty()) return;
        List<String> frames = swishFrames(text);
        if (frames.isEmpty()) {
            player.sendTitle(BASE + text, subtitle == null ? "" : subtitle, 0, stayTicks, 0);
            return;
        }

        new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                if (!player.isOnline() || index >= Math.min(frames.size(), loops)) {
                    cancel();
                    return;
                }
                player.sendTitle(frames.get(index), subtitle == null ? "" : subtitle, 0, stayTicks, 0);
                index++;
            }
        }.runTaskTimer(plugin, 0L, Math.max(1L, intervalTicks));
    }

    private static List<String> swishFrames(String text) {
        List<String> frames = new ArrayList<>();
        for (int focus = 0; focus < text.length(); focus++) {
            StringBuilder frame = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (i == focus) frame.append(FOCUS);
                else if (Math.abs(i - focus) == 1) frame.append(TRAIL);
                else frame.append(BASE);
                frame.append(c);
            }
            frames.add(frame.toString());
        }
        return frames;
    }

    private static String hex(String hex) {
        String cleaned = hex.replace("#", "");
        if (cleaned.length() != 6) return "§d";
        StringBuilder out = new StringBuilder("§x");
        for (char c : cleaned.toCharArray()) out.append('§').append(c);
        return out.toString();
    }
}
