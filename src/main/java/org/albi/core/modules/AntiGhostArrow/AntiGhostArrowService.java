package org.albi.core.modules.AntiGhostArrow;

import org.albi.core.Core;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AntiGhostArrowService {

    private final Core plugin;
    private final AntiGhostArrowConfig config;

    private final Map<UUID, ArrowSnapshot> arrowSnapshots = new HashMap<>();
    private final Map<UUID, Set<UUID>> forcedTargetsByArrow = new HashMap<>();
    private final Set<UUID> consumedArrows = new HashSet<>();
    private Map<UUID, PlayerSnapshot> playerSnapshots = new HashMap<>();

    private BukkitTask tickTask;
    private long tickCounter;
    private boolean reflectionWarningLogged;

    public AntiGhostArrowService(Core plugin, AntiGhostArrowConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(new AntiGhostArrowListener(this), plugin);
        bootstrapOnlinePlayers();
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("[AntiGhostArrow] Service started. enabled=" + config.isEnabled()
                + " debug=" + config.isDebug()
                + " reflectionSupported=" + AntiGhostArrowReflectionAccess.isSupported());
    }

    public void registerArrow(AbstractArrow arrow) {
        if (!config.isEnabled()) {
            if (config.isDebug()) {
                plugin.getLogger().info("[AntiGhostArrow] registerArrow skipped: module disabled");
            }
            return;
        }
        if (!shouldTrack(arrow)) {
            if (config.isDebug()) {
                plugin.getLogger().info("[AntiGhostArrow] registerArrow rejected: shouldTrack=false"
                        + " arrow=" + (arrow == null ? "null" : arrow.getUniqueId())
                        + " type=" + (arrow == null ? "null" : arrow.getType())
                        + " world=" + (arrow == null ? "null" : arrow.getWorld().getName())
                        + " valid=" + (arrow != null && arrow.isValid())
                        + " dead=" + (arrow != null && arrow.isDead())
                        + " inBlock=" + (arrow != null && arrow.isInBlock()));
            }
            return;
        }

        arrowSnapshots.put(arrow.getUniqueId(), ArrowSnapshot.capture(arrow, tickCounter));
        forcedTargetsByArrow.computeIfAbsent(arrow.getUniqueId(), ignored -> new HashSet<>());

        if (config.isDebug()) {
            plugin.getLogger().info("[AntiGhostArrow] Registered arrow " + arrow.getUniqueId()
                    + " type=" + arrow.getType()
                    + " world=" + arrow.getWorld().getName()
                    + " pos=" + format(arrow.getLocation().toVector()));
        }
    }

    public void unregisterArrow(Entity entity) {
        arrowSnapshots.remove(entity.getUniqueId());
        forcedTargetsByArrow.remove(entity.getUniqueId());
        consumedArrows.remove(entity.getUniqueId());
    }

    public void markConsumed(UUID uuid) {
        consumedArrows.add(uuid);
    }

    public void clearPlayer(UUID playerId) {
        playerSnapshots.remove(playerId);
    }

    private void bootstrapOnlinePlayers() {
        Map<UUID, PlayerSnapshot> initialSnapshots = new HashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isExempt(player.getWorld())) {
                continue;
            }
            initialSnapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        }
        this.playerSnapshots = initialSnapshots;
    }

    private void tick() {
        tickCounter++;

        if (!config.isEnabled()) {
            bootstrapOnlinePlayers();
            arrowSnapshots.clear();
            forcedTargetsByArrow.clear();
            consumedArrows.clear();
            return;
        }

        Map<UUID, PlayerSnapshot> currentPlayerSnapshots = captureCurrentPlayers();

        Iterator<Map.Entry<UUID, ArrowSnapshot>> iterator = arrowSnapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArrowSnapshot> entry = iterator.next();
            UUID arrowId = entry.getKey();

            AbstractArrow arrow = resolveArrow(arrowId);

            // Entity completely gone from the world — nothing we can do
            if (arrow == null || !isTrackableType(arrow)) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[AntiGhostArrow] Arrow " + arrowId
                            + " dropped: entity null or non-trackable type");
                }
                iterator.remove();
                cleanupArrow(arrowId);
                continue;
            }

            ArrowSnapshot previous = entry.getValue();
            ArrowSnapshot current = ArrowSnapshot.capture(arrow, tickCounter);
            if (current == null) {
                iterator.remove();
                cleanupArrow(arrowId);
                continue;
            }

            // Max lifetime exceeded
            if (current.ticksLived() > config.getMaxTrackedTicks()) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[AntiGhostArrow] Arrow " + arrowId
                            + " dropped: exceeded max tracked ticks (" + current.ticksLived() + ")");
                }
                iterator.remove();
                cleanupArrow(arrowId);
                continue;
            }

            boolean moved = current.ticksLived() > previous.ticksLived();
            boolean alreadyConsumed = consumedArrows.contains(arrowId);
            boolean done = !arrow.isValid() || arrow.isDead() || arrow.isInBlock();

            // ── Ghost detection ──────────────────────────────────────
            // Run if the arrow moved AND wasn't already consumed by a
            // vanilla entity hit. Crucially this runs BEFORE we check
            // "done", so arrows that just hit a block still get one
            // final ghost scan along their travel path.
            if (moved && !alreadyConsumed) {
                GhostHitCandidate candidate = findGhostHit(arrow, previous, current, currentPlayerSnapshots);
                if (candidate != null) {
                    forceGhostHit(arrow, candidate);
                    consumedArrows.add(arrowId);
                    iterator.remove();
                    cleanupArrow(arrowId);

                    if (config.isRemoveArrowOnForcedHit()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (arrow.isValid() && !arrow.isDead()) {
                                AntiGhostArrowReflectionAccess.discardArrow(arrow);
                            }
                        });
                    }
                    continue;
                }
            }

            // ── Cleanup finished arrows ──────────────────────────────
            if (done) {
                if (config.isDebug() && moved && !alreadyConsumed) {
                    plugin.getLogger().info("[AntiGhostArrow] Arrow " + arrowId
                            + " ended with no ghost detected (inBlock=" + arrow.isInBlock()
                            + " dead=" + arrow.isDead()
                            + " from=" + format(previous.position())
                            + " to=" + format(current.position()) + ")");
                }
                iterator.remove();
                cleanupArrow(arrowId);
                continue;
            }

            // Arrow still in flight — update snapshot for next tick
            entry.setValue(current);
        }

        this.playerSnapshots = currentPlayerSnapshots;
    }

    private Map<UUID, PlayerSnapshot> captureCurrentPlayers() {
        Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || isExempt(player.getWorld())) {
                continue;
            }
            snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        }
        return snapshots;
    }

    private AbstractArrow resolveArrow(UUID uuid) {
        Entity entity = plugin.getServer().getEntity(uuid);
        if (!(entity instanceof AbstractArrow arrow)) {
            return null;
        }
        return arrow;
    }

    private GhostHitCandidate findGhostHit(AbstractArrow arrow,
            ArrowSnapshot previousArrow,
            ArrowSnapshot currentArrow,
            Map<UUID, PlayerSnapshot> currentPlayerSnapshots) {
        Vector start = previousArrow.position();
        Vector end = currentArrow.position();
        Vector travel = end.clone().subtract(start);
        double travelDistanceSquared = travel.lengthSquared();
        double minimumTravel = config.getMinimumTravelDistance();
        if (travelDistanceSquared < minimumTravel * minimumTravel) {
            return null;
        }

        BoundingBox arrowTravelBox = between(start, end).expand(config.getSegmentExpansion());
        GhostHitCandidate best = null;
        Set<UUID> forcedTargets = forcedTargetsByArrow.computeIfAbsent(arrow.getUniqueId(), ignored -> new HashSet<>());

        int subSteps = Math.max(1, config.getSubSteps());

        for (Map.Entry<UUID, PlayerSnapshot> entry : currentPlayerSnapshots.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!isEligibleTarget(arrow, player, forcedTargets)) {
                continue;
            }

            PlayerSnapshot currentPlayer = entry.getValue();
            PlayerSnapshot previousPlayer = playerSnapshots.getOrDefault(player.getUniqueId(), currentPlayer);

            // Calculate relative velocity to add dynamic expansion if closing in
            double relativeVelocityExp = 0.0;
            if (previousArrow.velocity() != null && previousPlayer.velocity() != null) {
                Vector arrowDir = previousArrow.velocity().clone().normalize();
                if (arrowDir.lengthSquared() > 0) {
                    Vector playerVel = currentPlayer.velocity();
                    if (playerVel.lengthSquared() > 0) {
                        Vector p2a = arrow.getLocation().toVector().subtract(player.getLocation().toVector())
                                .normalize();
                        double approachSpeed = playerVel.dot(p2a);
                        // If they are moving toward the arrow, expand the hitbox slightly
                        if (approachSpeed > 0) {
                            relativeVelocityExp = Math.min(approachSpeed, config.getRelativeVelocityExpansion());
                        }
                    }
                }
            }

            double totalExpansion = config.getHitboxExpansion() + relativeVelocityExp;

            // Broad phase check
            BoundingBox sweptTargetBox = union(previousPlayer.boundingBox(), currentPlayer.boundingBox())
                    .expand(totalExpansion);

            if (!arrowTravelBox.overlaps(sweptTargetBox)) {
                continue;
            }

            // Narrow phase sub-tick interpolation
            for (int i = 0; i < subSteps; i++) {
                double t0 = (double) i / subSteps;
                double t1 = (double) (i + 1) / subSteps;

                Vector a0 = interpolate(start, end, t0);
                Vector a1 = interpolate(start, end, t1);

                BoundingBox pBox0 = interpolateBox(previousPlayer.boundingBox(), currentPlayer.boundingBox(), t0);
                BoundingBox pBox1 = interpolateBox(previousPlayer.boundingBox(), currentPlayer.boundingBox(), t1);

                BoundingBox subSweptBox = union(pBox0, pBox1).expand(totalExpansion);

                SegmentHit hit = intersectSegment(subSweptBox, a0, a1);
                if (hit != null) {
                    // We found a sub-tick collision. Calculate distance from ORIGINAL start
                    // so we get the earliest hit along the full path duration if there are
                    // multiple.
                    double distSqFromStart = hit.hitPosition().distanceSquared(start);
                    if (best == null || distSqFromStart < best.distanceSquared()) {
                        best = new GhostHitCandidate(player, hit.hitPosition(), distSqFromStart);
                    }
                    break; // No need to check further sub-steps for this player, we got the earliest hit
                }
            }
        }

        return best;
    }

    private boolean isEligibleTarget(AbstractArrow arrow, Player player, Set<UUID> forcedTargets) {
        if (player.isDead() || !player.isValid()) {
            return false;
        }
        if (config.isIgnoreSpectators() && player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (config.isIgnoreCreativePlayers() && player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        if (config.isIgnorePlayersWithNoDamageTicks() && player.getNoDamageTicks() > 0) {
            return false;
        }
        if (forcedTargets.contains(player.getUniqueId())) {
            return false;
        }

        ProjectileSource shooter = arrow.getShooter();
        if (shooter instanceof Entity shooterEntity
                && shooterEntity.getUniqueId().equals(player.getUniqueId())
                && arrow.getTicksLived() <= config.getOwnerGraceTicks()) {
            return false;
        }

        return player.getWorld().equals(arrow.getWorld());
    }

    private void forceGhostHit(AbstractArrow arrow, GhostHitCandidate candidate) {
        if (!AntiGhostArrowReflectionAccess.isSupported()) {
            if (!reflectionWarningLogged) {
                reflectionWarningLogged = true;
                plugin.getLogger().warning(
                        "[AntiGhostArrow] Reflection hooks are unavailable on this runtime. Ghost-hit recovery is disabled.");
            }
            return;
        }

        try {
            AntiGhostArrowReflectionAccess.forceHit(arrow, candidate.player(), candidate.hitPosition());
            forcedTargetsByArrow.computeIfAbsent(arrow.getUniqueId(), ignored -> new HashSet<>())
                    .add(candidate.player().getUniqueId());

            if (config.isDebug()) {
                plugin.getLogger().info("[AntiGhostArrow] Forced missed hit for arrow=" + arrow.getUniqueId()
                        + " target=" + candidate.player().getName()
                        + " at=" + format(candidate.hitPosition()));
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[AntiGhostArrow] Failed to force ghost hit: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    /**
     * Check used when deciding whether to start tracking a newly launched arrow.
     * Only checks type and world — does NOT check isValid()/isDead()/isInBlock()
     * because during {@code ProjectileLaunchEvent} the entity hasn't been fully
     * added to the world yet and {@code isValid()} always returns {@code false}.
     * The tick task handles cleanup of dead/inBlock arrows.
     */
    private boolean shouldTrack(AbstractArrow arrow) {
        if (arrow == null) {
            return false;
        }
        EntityType type = arrow.getType();
        if (type != EntityType.ARROW && type != EntityType.SPECTRAL_ARROW) {
            return false;
        }
        if (isExempt(arrow.getWorld())) {
            return false;
        }
        return true;
    }

    /**
     * Loose check - used during tick iteration.
     * Unlike {@link #shouldTrack}, this does NOT reject dead/inBlock arrows
     * so we can still perform a final ghost scan before cleanup.
     */
    private boolean isTrackableType(AbstractArrow arrow) {
        if (arrow == null) {
            return false;
        }
        EntityType type = arrow.getType();
        if (type != EntityType.ARROW && type != EntityType.SPECTRAL_ARROW) {
            return false;
        }
        return !isExempt(arrow.getWorld());
    }

    private void cleanupArrow(UUID arrowId) {
        forcedTargetsByArrow.remove(arrowId);
        consumedArrows.remove(arrowId);
    }

    private boolean isExempt(World world) {
        return config.getExemptWorlds().stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private static Vector interpolate(Vector v1, Vector v2, double t) {
        return new Vector(
                v1.getX() + (v2.getX() - v1.getX()) * t,
                v1.getY() + (v2.getY() - v1.getY()) * t,
                v1.getZ() + (v2.getZ() - v1.getZ()) * t);
    }

    private static BoundingBox interpolateBox(BoundingBox b1, BoundingBox b2, double t) {
        return new BoundingBox(
                b1.getMinX() + (b2.getMinX() - b1.getMinX()) * t,
                b1.getMinY() + (b2.getMinY() - b1.getMinY()) * t,
                b1.getMinZ() + (b2.getMinZ() - b1.getMinZ()) * t,
                b1.getMaxX() + (b2.getMaxX() - b1.getMaxX()) * t,
                b1.getMaxY() + (b2.getMaxY() - b1.getMaxY()) * t,
                b1.getMaxZ() + (b2.getMaxZ() - b1.getMaxZ()) * t);
    }

    private static BoundingBox between(Vector a, Vector b) {
        return new BoundingBox(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    private static BoundingBox union(BoundingBox first, BoundingBox second) {
        return new BoundingBox(
                Math.min(first.getMinX(), second.getMinX()),
                Math.min(first.getMinY(), second.getMinY()),
                Math.min(first.getMinZ(), second.getMinZ()),
                Math.max(first.getMaxX(), second.getMaxX()),
                Math.max(first.getMaxY(), second.getMaxY()),
                Math.max(first.getMaxZ(), second.getMaxZ()));
    }

    private static SegmentHit intersectSegment(BoundingBox box, Vector start, Vector end) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();

        double tMin = 0.0D;
        double tMax = 1.0D;

        tMin = clipAxis(start.getX(), dx, box.getMinX(), box.getMaxX(), tMin, true);
        tMax = clipAxis(start.getX(), dx, box.getMinX(), box.getMaxX(), tMax, false);
        if (tMax < tMin) {
            return null;
        }

        tMin = clipAxis(start.getY(), dy, box.getMinY(), box.getMaxY(), tMin, true);
        tMax = clipAxis(start.getY(), dy, box.getMinY(), box.getMaxY(), tMax, false);
        if (tMax < tMin) {
            return null;
        }

        tMin = clipAxis(start.getZ(), dz, box.getMinZ(), box.getMaxZ(), tMin, true);
        tMax = clipAxis(start.getZ(), dz, box.getMinZ(), box.getMaxZ(), tMax, false);
        if (tMax < tMin) {
            return null;
        }

        Vector hit = start.clone().add(new Vector(dx * tMin, dy * tMin, dz * tMin));
        return new SegmentHit(hit, hit.distanceSquared(start));
    }

    private static double clipAxis(double start, double delta, double min, double max, double value, boolean lower) {
        if (Math.abs(delta) < 1.0E-9D) {
            if (start < min || start > max) {
                return lower ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }
            return value;
        }

        double inverse = 1.0D / delta;
        double first = (min - start) * inverse;
        double second = (max - start) * inverse;
        double entry = Math.min(first, second);
        double exit = Math.max(first, second);
        return lower ? Math.max(value, entry) : Math.min(value, exit);
    }

    private static String format(Vector vector) {
        return String.format(java.util.Locale.ROOT, "%.3f, %.3f, %.3f", vector.getX(), vector.getY(), vector.getZ());
    }

    private record GhostHitCandidate(Player player, Vector hitPosition, double distanceSquared) {
    }

    private record SegmentHit(Vector hitPosition, double distanceSquared) {
    }
}
