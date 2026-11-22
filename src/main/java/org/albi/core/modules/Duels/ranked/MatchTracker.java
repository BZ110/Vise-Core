package org.albi.core.modules.Duels.ranked;

import org.albi.core.util.ConfigValues;

import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.albi.core.modules.Duels.AutomatedCombat.ReachCalculator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchTracker implements Listener {

    private static MatchTracker instance;
    private final Core plugin;
    private final Map<UUID, RankedMatchRecord> playerToMatch = new ConcurrentHashMap<>();
    private final Set<RankedMatchRecord> activeMatches = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<RankedMatchRecord, UUID> lastHitter = new ConcurrentHashMap<>();
    private final Map<RankedMatchRecord, Integer> currentCombo = new ConcurrentHashMap<>();
    private final Map<RankedMatchRecord, BukkitTask> trackerTasks = new ConcurrentHashMap<>();

    private MatchTracker(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startPingSampler();
    }

    public static MatchTracker get(Core plugin) {
        if (instance == null) {
            instance = new MatchTracker(plugin);
        }
        return instance;
    }

    public void startTracking(RankedMatchRecord record) {
        activeMatches.add(record);
        playerToMatch.put(record.getPlayer1(), record);
        playerToMatch.put(record.getPlayer2(), record);

        int sampleTicks = ConfigValues.integer(plugin.getConfig(), "ranked.telemetry.snapshot_interval_ticks", 20, 5, 1200);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player p1 = Bukkit.getPlayer(record.getPlayer1());
                Player p2 = Bukkit.getPlayer(record.getPlayer2());
                if (p1 != null && p2 != null) {
                    record.addSnapshot(
                            p1.getLocation().getX(), p1.getLocation().getY(), p1.getLocation().getZ(), p1.getHealth(),
                            getPing(p1),
                            p2.getLocation().getX(), p2.getLocation().getY(), p2.getLocation().getZ(), p2.getHealth(),
                            getPing(p2));
                }
            }
        }.runTaskTimer(plugin, sampleTicks, sampleTicks);
        trackerTasks.put(record, task);
    }

    public void stopTracking(RankedMatchRecord record) {
        activeMatches.remove(record);
        playerToMatch.remove(record.getPlayer1());
        playerToMatch.remove(record.getPlayer2());
        lastHitter.remove(record);
        currentCombo.remove(record);

        BukkitTask task = trackerTasks.remove(record);
        if (task != null) {
            task.cancel();
        }

        record.end();

        if (!record.getHits().isEmpty()) {
            double avgReach = record.getHits().stream()
                    .mapToDouble(RankedMatchRecord.HitRecord::distance)
                    .average()
                    .orElse(0.0D);

            if (avgReach > 3.5D) {
                // Keep this as logging only for now.
                // Now the value comes from ray versus hitbox math, not eye to entity points.
            }
        }

        StatManagement.saveMatchRecord(record);
        WebhookService.sendMatchSummary(plugin, record);
    }

    public void onHit(Player attacker, Player victim) {
        RankedMatchRecord record = playerToMatch.get(attacker.getUniqueId());
        if (record == null) {
            return;
        }

        boolean sameMatch = record.getPlayer1().equals(victim.getUniqueId())
                || record.getPlayer2().equals(victim.getUniqueId());
        if (!sameMatch) {
            return;
        }

        ReachCalculator.ReachResult reachResult = ReachCalculator.measure(attacker, victim);

        double loggedReach = reachResult.getDistance();
        double yDiff = attacker.getLocation().getY() - victim.getLocation().getY();

        record.addHit(
                attacker.getUniqueId(),
                loggedReach,
                attacker.getLocation().getYaw(),
                attacker.getLocation().getPitch(),
                yDiff);

        UUID last = lastHitter.get(record);
        int combo = currentCombo.getOrDefault(record, 0);

        if (attacker.getUniqueId().equals(last)) {
            combo++;
        } else {
            combo = 1;
        }

        lastHitter.put(record, attacker.getUniqueId());
        currentCombo.put(record, combo);

        if (attacker.getUniqueId().equals(record.getPlayer1())) {
            if (combo > record.getMaxCombo1()) {
                record.setMaxCombo1(combo);
            }
        } else {
            if (combo > record.getMaxCombo2()) {
                record.setMaxCombo2(combo);
            }
        }

        // For now, just keep the result available for future logic.
        // reachResult.isInvalidHitbox() means the player was not actually aiming
        // through the victim hitbox.
        // reachResult.isReachViolation() means the ray hit the hitbox, but too far
        // away.
    }

    private void startPingSampler() {
        int sampleTicks = ConfigValues.integer(plugin.getConfig(), "ranked.telemetry.ping_interval_ticks", 100, 5, 2400);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (RankedMatchRecord record : activeMatches) {
                    Player p1 = Bukkit.getPlayer(record.getPlayer1());
                    Player p2 = Bukkit.getPlayer(record.getPlayer2());
                    if (p1 != null) {
                        record.addPing(p1.getUniqueId(), getPing(p1));
                    }
                    if (p2 != null) {
                        record.addPing(p2.getUniqueId(), getPing(p2));
                    }
                }
            }
        }.runTaskTimer(plugin, sampleTicks, sampleTicks);
    }

    private int getPing(Player player) {
        return player.getPing();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            RankedMatchRecord record = playerToMatch.get(event.getPlayer().getUniqueId());
            if (record != null) {
                record.addClick(event.getPlayer().getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            RankedMatchRecord record = playerToMatch.get(attacker.getUniqueId());
            if (record != null && (record.getPlayer2().equals(victim.getUniqueId())
                    || record.getPlayer1().equals(victim.getUniqueId()))) {
                record.addDamage(attacker.getUniqueId(), event.getFinalDamage());
            }
        }
    }
}