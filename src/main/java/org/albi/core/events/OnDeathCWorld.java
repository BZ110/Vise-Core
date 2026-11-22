package org.albi.core.events;

import org.albi.core.Core;
import org.albi.core.data.ExtendedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OnDeathCWorld implements Listener {

    private final Core plugin;
    private final Set<UUID> respawnToLobby = ConcurrentHashMap.newKeySet();

    public OnDeathCWorld(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        e.setDeathMessage(null);

        Player victim = e.getEntity();
        World world = victim.getWorld();

        boolean inCWorld = plugin.getConfig()
                .getStringList("c_worlds")
                .stream()
                .anyMatch(w -> w.equalsIgnoreCase(world.getName()));

        if (!inCWorld) {
            return;
        }

        ExtendedPlayer eP = plugin.getExtendedPlayer(victim);

        // IMPORTANT: read lastHit BEFORE clearing it
        Player lastHit = eP != null ? eP.getLastHit() : null;
        Player killer = victim.getKiller();
        Player effectiveKiller = killer != null ? killer : lastHit;

        if (eP != null) {
            eP.setDeathLocation(victim.getLocation());
        }

        String msg;
        if (effectiveKiller != null) {
            msg = ChatColor.translateAlternateColorCodes('&',
                    "&a" + victim.getName() +
                            " &7has been killed by &c" +
                            effectiveKiller.getName());
        } else {
            msg = ChatColor.translateAlternateColorCodes('&',
                    "&a" + victim.getName() +
                            " &7has been killed.");
        }

        for (Player wPlayer : world.getPlayers()) {
            wPlayer.sendMessage(msg);
        }

        org.albi.core.managers.cosmetics.DeathEffectManager.playKillEffect(
                effectiveKiller,
                victim.getLocation(),
                world.getPlayers(),
                plugin);

        if (eP != null) {
            eP.setLastHit(null);
        }

        // Mark this player to be sent to lobby AFTER respawn
        respawnToLobby.add(victim.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();

        if (!respawnToLobby.remove(player.getUniqueId())) {
            return;
        }

        // Move to lobby on next tick, after the respawn is fully complete
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.resetPlayer(player);
            plugin.teleportToLobby(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        respawnToLobby.remove(e.getPlayer().getUniqueId());
    }
}