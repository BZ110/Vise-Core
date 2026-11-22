package org.albi.core.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class GuiSoundUtil {
    private GuiSoundUtil() {}

    public static void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);
    }

    public static void positive(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.1f);
    }

    public static void negative(Player player) {
        play(player, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    public static void page(Player player) {
        play(player, Sound.ITEM_BOOK_PAGE_TURN, 0.85f, 1.0f);
    }

    public static void soft(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.1f);
    }

    public static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {}
    }
}
