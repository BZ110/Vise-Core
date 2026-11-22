package org.albi.core.managers.cosmetics;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.albi.core.util.ConfigValues;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class DeathEffectManager {

    public static boolean isEnabled(Core plugin, DeathEffect effect) {
        return effect == DeathEffect.NONE || (plugin.getConfig().getBoolean("cosmetics.kill_effects.enabled", true)
                && plugin.getConfig().getStringList("cosmetics.kill_effects.disabled_effects").stream()
                        .noneMatch(name -> name.equalsIgnoreCase(effect.name())));
    }

    public static int requiredLevel(Core plugin, DeathEffect effect) {
        return ConfigValues.integer(plugin.getConfig(), "cosmetics.kill_effects.unlock_levels."
                + effect.name().toLowerCase(Locale.ROOT), effect.getRequiredLevel(), 0, 10_000);
    }

    /**
     * Called when a player (killer) gets a kill.
     * The effect belongs to the KILLER and plays at the VICTIM's location.
     * Both players hear/see it.
     *
     * @param killer        The player who made the kill (effect owner), may be null
     * @param victimLoc     The location of the victim (where the effect plays)
     * @param nearbyPlayers All players who should hear the sound
     * @param plugin        Plugin instance
     */
    public static void playKillEffect(Player killer, Location victimLoc, Collection<Player> nearbyPlayers, Core plugin) {
        if (killer == null || victimLoc == null) return;

        ExtendedPlayer ep = plugin.getExtendedPlayer(killer);
        if (ep == null) return;
        MainPlayer mp = ep.getMainPlayer();
        if (mp == null) return;

        String selected = mp.getDeathEffect();
        if (selected == null || selected.equalsIgnoreCase("NONE")) return;

        DeathEffect effect;
        try {
            effect = DeathEffect.valueOf(selected);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (!isEnabled(plugin, effect)) return;
        boolean unlocked = mp.level() >= requiredLevel(plugin, effect)
                || killer.hasPermission("core.deatheffect.*")
                || killer.hasPermission("core.deatheffect." + effect.name().toLowerCase())
                || killer.isOp();

        if (!unlocked) return;

        // Filter players who have kill effects enabled
        List<Player> viewers = nearbyPlayers.stream()
                .filter(p -> {
                    ExtendedPlayer epV = plugin.getExtendedPlayer(p);
                    return epV != null && epV.getMainPlayer() != null && epV.getMainPlayer().showKillEffects();
                })
                .toList();

        if (viewers.isEmpty()) return;

        // Play particles for visible players only
        effect.play(victimLoc, viewers, plugin);

        // Play sound for visible players only
        Sound sound = effect.getSound();
        if (sound != null && plugin.getConfig().getBoolean("cosmetics.kill_effects.play_sound", true)) {
            float volume = (float) ConfigValues.decimal(plugin.getConfig(), "cosmetics.kill_effects.volume", 1.5, 0, 4);
            for (Player p : viewers) {
                p.playSound(victimLoc, sound, volume, effect.getSoundPitch());
            }
        }
    }
}
