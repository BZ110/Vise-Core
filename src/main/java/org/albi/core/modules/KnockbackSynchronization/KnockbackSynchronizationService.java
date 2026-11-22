package org.albi.core.modules.KnockbackSynchronization;

import org.albi.core.Core;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KnockbackSynchronizationService {

    private static final double VANILLA_SPRINTING_VERTICAL = 0.4D;
    private static final double VANILLA_STANDARD_VERTICAL = 0.36080000519752503D;

    private final Core plugin;
    private final KnockbackSynchronizationConfig config;
    private final Map<UUID, KnockbackTrackedPlayerState> states = new ConcurrentHashMap<>();

    private BukkitTask samplerTask;
    private BukkitTask cleanupTask;

    public KnockbackSynchronizationService(Core plugin, KnockbackSynchronizationConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(new KnockbackSynchronizationListener(this), plugin);

        if (config.isRunnableEnabled()) {
            this.samplerTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::sampleCombatPings,
                    1L,
                    config.getSampleIntervalTicks()
            );
        }

        this.cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupExpiredStates, 20L, 20L);
    }

    public void recordDamage(Player victim, Player attacker) {
        if (!config.isEnabled() || isExempt(victim)) {
            return;
        }

        KnockbackTrackedPlayerState state = states.computeIfAbsent(
                victim.getUniqueId(),
                key -> new KnockbackTrackedPlayerState()
        );

        double knockbackResistance = KnockbackReflectionAccess.getKnockbackResistance(victim, 0.0D);

        state.samplePing(victim);
        state.setGravity(KnockbackReflectionAccess.getGravity(victim, 0.08D));
        state.setVerticalVelocity(calculateVerticalVelocity(attacker, knockbackResistance));
        state.setLastDamageTicks(victim.getNoDamageTicks());
        state.setExpiresAtMillis(System.currentTimeMillis() + (config.getCombatTimeoutTicks() * 50L));

        debug("Recorded damage for " + victim.getName()
                + " ping=" + fmt(state.getPing(victim))
                + " previousPing=" + fmt(state.getPreviousPing(victim))
                + " expectedY=" + fmt(state.getVerticalVelocity()));
    }

    public void applyKnockbackSync(PlayerVelocityEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Player victim = event.getPlayer();
        if (isExempt(victim)) {
            return;
        }

        KnockbackTrackedPlayerState state = getActiveState(victim);
        if (state == null) {
            return;
        }

        EntityDamageEvent lastDamage = victim.getLastDamageCause();
        if (!(lastDamage instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        if (byEntity.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(byEntity.getDamager() instanceof Player)) {
            return;
        }

        double ping = state.getPing(victim);
        if (ping < config.getPingOffset()) {
            return;
        }

        if (shouldSkipEnvironment(victim)) {
            return;
        }

        double distanceToGround = getDistanceToGround(victim);
        if (distanceToGround <= 0.0D) {
            return;
        }

        Vector incomingVelocity = event.getVelocity().clone();
        Vector currentVelocity = victim.getVelocity().clone();

        // Ground prediction must use the player's current motion before this hit is applied.
        // Using the incoming knockback packet here can turn mid-air re-hits into fake "client grounded"
        // cases and causes the artificial vertical-only jump bug.
        if (isOnGroundClientSide(state, victim, currentVelocity.getY(), distanceToGround)) {
            Integer lastDamageTicks = state.getLastDamageTicks();
            if (lastDamageTicks != null && lastDamageTicks > config.getMaxNoDamageTicks()) {
                return;
            }

            Double adjustedVertical = state.getVerticalVelocity();
            if (adjustedVertical == null) {
                return;
            }

            event.setVelocity(new Vector(currentVelocity.getX(), adjustedVertical, currentVelocity.getZ()));
            debug("Injected grounded vertical knockback for " + victim.getName()
                    + " currentY=" + fmt(currentVelocity.getY())
                    + " incomingY=" + fmt(incomingVelocity.getY())
                    + " currentX=" + fmt(currentVelocity.getX())
                    + " currentZ=" + fmt(currentVelocity.getZ())
                    + " incomingX=" + fmt(incomingVelocity.getX())
                    + " incomingZ=" + fmt(incomingVelocity.getZ())
                    + " adjustedY=" + fmt(adjustedVertical)
                    + " distanceToGround=" + fmt(distanceToGround)
                    + " ping=" + fmt(ping));
            return;
        }

        if (!config.isOffGroundSynchronization()) {
            return;
        }

        double compensatedY = KnockbackSynchronizationMath.getCompensatedVerticalVelocity(
                currentVelocity.getY(),
                state.getGravity(),
                state.getTicks(victim, config)
        );
        event.setVelocity(new Vector(currentVelocity.getX(), compensatedY, currentVelocity.getZ()));
        debug("Applied off-ground vertical knockback for " + victim.getName()
                + " currentY=" + fmt(currentVelocity.getY())
                + " incomingY=" + fmt(incomingVelocity.getY())
                + " currentX=" + fmt(currentVelocity.getX())
                + " currentZ=" + fmt(currentVelocity.getZ())
                + " incomingX=" + fmt(incomingVelocity.getX())
                + " incomingZ=" + fmt(incomingVelocity.getZ())
                + " adjustedY=" + fmt(compensatedY)
                + " distanceToGround=" + fmt(distanceToGround)
                + " ping=" + fmt(ping));
    }

    private boolean isOnGroundClientSide(KnockbackTrackedPlayerState state,
                                         Player victim,
                                         double verticalVelocity,
                                         double distanceToGround) {
        int timeToMax = verticalVelocity > 0.0D
                ? KnockbackSynchronizationMath.calculateTimeToMaxVelocity(verticalVelocity, state.getGravity())
                : 0;
        if (timeToMax == -1) {
            return false;
        }

        double maxHeight = verticalVelocity > 0.0D
                ? KnockbackSynchronizationMath.calculateDistanceTraveled(verticalVelocity, timeToMax, state.getGravity())
                : 0.0D;

        int fallTime = KnockbackSynchronizationMath.calculateFallTime(
                verticalVelocity,
                maxHeight + distanceToGround,
                state.getGravity()
        );
        if (fallTime == -1) {
            return false;
        }

        return (timeToMax + fallTime) - state.getCompensatedTicks(victim, config) <= 0
                && distanceToGround <= config.getMaxGroundDistance();
    }

    private double getDistanceToGround(Player player) {
        double collisionDistance = config.getMaxRayDistance();
        World world = player.getWorld();
        BoundingBox boundingBox = player.getBoundingBox();
        double footY = player.getLocation().getY();
        double adjustment = 0.01D;

        Location[] corners = {
                new Location(world, boundingBox.getMinX() + adjustment, footY, boundingBox.getMinZ() + adjustment),
                new Location(world, boundingBox.getMinX() + adjustment, footY, boundingBox.getMaxZ() - adjustment),
                new Location(world, boundingBox.getMaxX() - adjustment, footY, boundingBox.getMinZ() + adjustment),
                new Location(world, boundingBox.getMaxX() - adjustment, footY, boundingBox.getMaxZ() - adjustment)
        };

        Vector down = new Vector(0, -1, 0);

        for (Location corner : corners) {
            RayTraceResult result = world.rayTraceBlocks(
                    corner,
                    down,
                    config.getMaxRayDistance(),
                    FluidCollisionMode.NEVER,
                    true
            );
            if (result == null || result.getHitBlock() == null) {
                continue;
            }

            collisionDistance = Math.min(collisionDistance, corner.getY() - result.getHitBlock().getY());
        }

        return collisionDistance - 1.0D;
    }

    public void clear(UUID uuid) {
        states.remove(uuid);
    }

    private KnockbackTrackedPlayerState getActiveState(Player player) {
        KnockbackTrackedPlayerState state = states.get(player.getUniqueId());
        if (state == null) {
            return null;
        }

        if (!player.isOnline() || state.isExpired(System.currentTimeMillis())) {
            states.remove(player.getUniqueId());
            return null;
        }

        return state;
    }

    private void sampleCombatPings() {
        if (!config.isEnabled() || !config.isRunnableEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, KnockbackTrackedPlayerState> entry : states.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || entry.getValue().isExpired(now)) {
                continue;
            }

            entry.getValue().samplePing(player);
        }
    }

    private void cleanupExpiredStates() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            return player == null || !player.isOnline() || entry.getValue().isExpired(now);
        });
    }

    private boolean isExempt(Player player) {
        return config.getExemptWorlds().stream()
                .anyMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()));
    }

    private boolean shouldSkipEnvironment(Player player) {
        if (player.isGliding()) {
            return true;
        }

        Block block = player.getLocation().getBlock();
        Material type = block.getType();
        if (type == Material.WATER
                || type == Material.LAVA
                || type == Material.COBWEB
                || type == Material.SCAFFOLDING) {
            return true;
        }

        return block.isLiquid();
    }

    private double calculateVerticalVelocity(Player attacker, double knockbackResistance) {
        double verticalVelocity = attacker.getAttackCooldown() > 0.848D
                ? VANILLA_SPRINTING_VERTICAL
                : VANILLA_STANDARD_VERTICAL;

        if (!attacker.isSprinting()) {
            verticalVelocity = VANILLA_STANDARD_VERTICAL;
            verticalVelocity -= 0.04000000119D * knockbackResistance * 10.0D;
        }

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item != null && item.getEnchantmentLevel(Enchantment.KNOCKBACK) > 0) {
            verticalVelocity = VANILLA_SPRINTING_VERTICAL;
        }

        return verticalVelocity;
    }

    private void debug(String message) {
        if (config.isDebug()) {
            plugin.getLogger().info("[KnockbackSync] " + message);
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
