package org.albi.core.managers.cosmetics;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.albi.core.data.MainPlayer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

public class DeathEffectManager {

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

        // Check killer has the unlock
        boolean unlocked = mp.level() >= effect.getRequiredLevel()
                || killer.hasPermission("core.deatheffect.*")
                || killer.hasPermission("core.deatheffect." + effect.name().toLowerCase())
                || killer.isOp();

        if (!unlocked) return;

        // Filter players who have kill effects enabled
        List<Player> viewers = nearbyPlayers.stream()
                .filter(p -> {
                    ExtendedPlayer epV = plugin.getExtendedPlayer(p);
                    return epV != null && epV.getMainPlayer().showKillEffects();
                })
                .toList();

        if (viewers.isEmpty()) return;

        // Play particles for visible players only
        effect.play(victimLoc, viewers, plugin);

        // Play sound for visible players only
        Sound sound = effect.getSound();
        if (sound != null) {
            for (Player p : viewers) {
                p.playSound(victimLoc, sound, 1.5f, effect.getSoundPitch());
            }
        }
    }
}
