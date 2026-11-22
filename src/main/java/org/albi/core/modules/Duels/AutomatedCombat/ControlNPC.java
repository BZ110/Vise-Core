package org.albi.core.modules.Duels.AutomatedCombat;

import org.albi.mods.Reflect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class ControlNPC {

    private static final Logger LOG = Logger.getLogger("AutomatedCombat");

    private static final Class<?> C_ENTITY = Reflect.resolveClass("net.minecraft.world.entity.Entity");
    private static final Class<?> C_SERVER_PLAYER = Reflect.resolveClass("net.minecraft.server.level.ServerPlayer");
    private static final Class<?> C_VEC3 = Reflect.resolveClass("net.minecraft.world.phys.Vec3");
    private static final Class<?> C_INTERACTION_HAND = Reflect.resolveClass("net.minecraft.world.InteractionHand");
    private static final Class<?> C_EQUIPMENT_SLOT = Reflect.resolveClass("net.minecraft.world.entity.EquipmentSlot");
    private static final Class<?> C_ITEM_STACK = Reflect.resolveClass("net.minecraft.world.item.ItemStack");
    private static final Class<?> C_MOVER_TYPE = Reflect.resolveClass("net.minecraft.world.entity.MoverType");
    private static final Class<?> C_REMOVAL_REASON = Reflect
            .resolveClass("net.minecraft.world.entity.Entity$RemovalReason");
    private static final Class<?> C_INPUT = maybeClass("net.minecraft.world.entity.player.Input");

    private static final Constructor<?> CTOR_VEC3 = Reflect.resolveConstructor(C_VEC3, double.class, double.class,
            double.class);
    private static final Constructor<?> CTOR_INPUT = C_INPUT == null ? null
            : Reflect.resolveConstructor(C_INPUT,
                    boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class,
                    boolean.class);

    private static final Method M_GET_BUKKIT_ENTITY = Reflect.resolveMethod(C_ENTITY,
            new String[] { "getBukkitEntity" });
    private static final Method M_GET_DELTA_MOVEMENT = Reflect.resolveMethod(C_ENTITY,
            new String[] { "getDeltaMovement" });
    private static final Method M_ADD_DELTA_MOVEMENT = Reflect.resolveMethod(C_ENTITY,
            new String[] { "addDeltaMovement" }, C_VEC3);
    private static final Method M_SET_DELTA_MOVEMENT_VEC = Reflect.resolveMethod(C_ENTITY,
            new String[] { "setDeltaMovement" }, C_VEC3);
    private static final Method M_MOVE = Reflect.resolveMethod(C_ENTITY, new String[] { "move" }, C_MOVER_TYPE, C_VEC3);
    // hasImpulse is a boolean FIELD on Entity, not a method. Setting it to true
    // tells the entity tracker to send a velocity packet to the client.
    // NOTE: In obfuscated paper remaps, this may be 'c' or similar, so if it fails,
    // we use the Bukkit setVelocity API fallback which handles it internally.
    private static final Field F_HAS_IMPULSE = Reflect.resolveField(C_ENTITY, "hasImpulse");
    private static final Method M_SET_SPRINTING = Reflect.resolveMethod(C_ENTITY, new String[] { "setSprinting" },
            boolean.class);
    private static final Method M_SET_NO_GRAVITY = Reflect.resolveMethod(C_ENTITY, new String[] { "setNoGravity" },
            boolean.class);
    private static final Method M_SET_INVULNERABLE = Reflect.resolveMethod(C_ENTITY, new String[] { "setInvulnerable" },
            boolean.class);
    private static final Method M_SET_POS = Reflect.resolveMethod(C_ENTITY, new String[] { "setPos" }, double.class,
            double.class, double.class);
    private static final Method M_REMOVE = Reflect.resolveMethod(C_ENTITY, new String[] { "remove" }, C_REMOVAL_REASON);
    private static final Method M_UNSET_REMOVED = Reflect.resolveMethod(C_ENTITY, new String[] { "unsetRemoved" });
    private static final Method M_GET_ID = Reflect.resolveMethod(C_ENTITY, new String[] { "getId" });

    private static final Method M_SET_Y_ROT = Reflect.resolveMethod(C_ENTITY, new String[] { "setYRot" }, float.class);
    private static final Method M_SET_X_ROT = Reflect.resolveMethod(C_ENTITY, new String[] { "setXRot" }, float.class);
    private static final Method M_SET_Y_HEAD_ROT = Reflect.resolveMethod(C_ENTITY, new String[] { "setYHeadRot" },
            float.class);
    private static final Method M_SET_Y_BODY_ROT = Reflect.resolveMethod(C_ENTITY, new String[] { "setYBodyRot" },
            float.class);

    private static final Method M_ATTACK = Reflect.resolveMethod(C_SERVER_PLAYER, new String[] { "attack" }, C_ENTITY);
    // Resets the attack cooldown so getAttackStrengthScale() returns 1.0 (full
    // damage).
    // Without this, a fake player whose tick() doesn't advance the ticker deals 0
    // damage.
    private static final Method M_RESET_ATTACK_STRENGTH = Reflect.resolveMethod(
            C_SERVER_PLAYER, new String[] { "resetAttackStrengthTicker" });
    private static final Method M_SWING = Reflect.resolveMethod(C_SERVER_PLAYER, new String[] { "swing" },
            C_INTERACTION_HAND);
    private static final Method M_SNAP_TO = Reflect.resolveMethod(C_SERVER_PLAYER,
            new String[] { "snapTo", "moveTo", "absMoveTo" }, double.class, double.class, double.class);
    private static final Method M_TELEPORT_TO_3 = Reflect.resolveMethod(C_SERVER_PLAYER, new String[] { "teleportTo" },
            double.class, double.class, double.class);
    private static final Method M_START_USING_ITEM = Reflect.resolveMethod(C_SERVER_PLAYER,
            new String[] { "startUsingItem" }, C_INTERACTION_HAND);
    private static final Method M_RELEASE_USING_ITEM = Reflect.resolveMethod(C_SERVER_PLAYER,
            new String[] { "releaseUsingItem" });
    private static final Method M_SET_ITEM_SLOT = Reflect.resolveMethod(C_SERVER_PLAYER, new String[] { "setItemSlot" },
            C_EQUIPMENT_SLOT, C_ITEM_STACK);
    private static final Method M_SET_LAST_CLIENT_INPUT = C_INPUT == null ? null
            : Reflect.resolveMethod(C_SERVER_PLAYER, new String[] { "setLastClientInput", "setPlayerInput" }, C_INPUT);

    private static final Field F_VEC3_X = Reflect.resolveField(C_VEC3, "x", "f_82479_");
    private static final Field F_VEC3_Y = Reflect.resolveField(C_VEC3, "y", "f_82480_");
    private static final Field F_VEC3_Z = Reflect.resolveField(C_VEC3, "z", "f_82481_");

    private final Object serverPlayer;
    private final FakePlayerNPC npcWrapper;
    private int steeringLockTicks;
    private boolean debugEnabled;
    private double injectedKnockbackX;
    private double injectedKnockbackY;
    private double injectedKnockbackZ;
    private int knockbackInputBlendTicks;

    public ControlNPC(Object serverPlayer, FakePlayerNPC npcWrapper) {
        this.serverPlayer = serverPlayer;
        this.npcWrapper = npcWrapper;
    }

    public void applyKnockback(double kx, double ky, double kz) {
        this.injectedKnockbackX = kx;
        this.injectedKnockbackY = ky;
        this.injectedKnockbackZ = kz;
        this.knockbackInputBlendTicks = Math.max(this.knockbackInputBlendTicks, 6);
    }

    public void setDebug(boolean debug) {
        this.debugEnabled = debug;
    }

    private void debug(String msg) {
        if (debugEnabled)
            LOG.warning("[AutomatedCombat][DEBUG][ControlNPC] " + msg);
    }

    private static Class<?> maybeClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    public Object raw() {
        return serverPlayer;
    }

    public Player getBukkitPlayer() {
        if (serverPlayer == null || M_GET_BUKKIT_ENTITY == null)
            return null;
        Object entity = Reflect.invoke(serverPlayer, M_GET_BUKKIT_ENTITY);
        return entity instanceof Player player ? player : null;
    }

    public void initState() {
        if (serverPlayer == null)
            return;
        if (M_UNSET_REMOVED != null)
            Reflect.invoke(serverPlayer, M_UNSET_REMOVED);
        if (M_SET_INVULNERABLE != null)
            Reflect.invoke(serverPlayer, M_SET_INVULNERABLE, false);
        if (M_SET_NO_GRAVITY != null)
            Reflect.invoke(serverPlayer, M_SET_NO_GRAVITY, false);

        // Diagnostic dump at spawn time so we can see which reflections resolved.
        LOG.warning("[AutomatedCombat][DEBUG][ControlNPC] --- Reflection audit ---");
        LOG.warning("  F_HAS_IMPULSE       = " + (F_HAS_IMPULSE != null ? F_HAS_IMPULSE.getName() : "NULL"));
        LOG.warning("  M_ATTACK            = " + (M_ATTACK != null));
        LOG.warning("  M_RESET_ATTACK_STR  = " + (M_RESET_ATTACK_STRENGTH != null));
        LOG.warning("  M_SET_DELTA_MOV     = " + (M_SET_DELTA_MOVEMENT_VEC != null));
        LOG.warning("  M_GET_DELTA_MOV     = " + (M_GET_DELTA_MOVEMENT != null));
        LOG.warning("  M_MOVE              = " + (M_MOVE != null));
        LOG.warning("  M_SWING             = " + (M_SWING != null));
        LOG.warning("  M_SET_LAST_INPUT    = " + (M_SET_LAST_CLIENT_INPUT != null));
        LOG.warning("  CTOR_INPUT          = " + (CTOR_INPUT != null));
        LOG.warning("  --------------------------------");
    }

    public int getEntityId() {
        if (serverPlayer == null || M_GET_ID == null)
            return -1;
        Number id = Reflect.invoke(serverPlayer, M_GET_ID);
        return id == null ? -1 : id.intValue();
    }

    public void removeDiscarded() {
        if (serverPlayer == null || M_REMOVE == null)
            return;
        Object reason = Reflect.enumValue(C_REMOVAL_REASON, "DISCARDED");
        Reflect.invoke(serverPlayer, M_REMOVE, reason);
    }

    public void setPosition(double x, double y, double z) {
        if (serverPlayer == null)
            return;
        if (M_SNAP_TO != null) {
            Reflect.invoke(serverPlayer, M_SNAP_TO, x, y, z);
            return;
        }
        if (M_TELEPORT_TO_3 != null) {
            Reflect.invoke(serverPlayer, M_TELEPORT_TO_3, x, y, z);
            return;
        }
        if (M_SET_POS != null) {
            Reflect.invoke(serverPlayer, M_SET_POS, x, y, z);
        }
    }

    public void setRotation(float yaw, float pitch) {
        if (serverPlayer == null)
            return;
        if (M_SET_Y_ROT != null)
            Reflect.invoke(serverPlayer, M_SET_Y_ROT, yaw);
        if (M_SET_Y_HEAD_ROT != null)
            Reflect.invoke(serverPlayer, M_SET_Y_HEAD_ROT, yaw);
        if (M_SET_Y_BODY_ROT != null)
            Reflect.invoke(serverPlayer, M_SET_Y_BODY_ROT, yaw);
        if (M_SET_X_ROT != null)
            Reflect.invoke(serverPlayer, M_SET_X_ROT, pitch);
    }

    public void lockSteering(int ticks) {
        steeringLockTicks = Math.max(steeringLockTicks, ticks);
    }

    public boolean isSteeringLocked() {
        return steeringLockTicks > 0;
    }

    public void tickSteeringLock() {
        if (steeringLockTicks > 0)
            steeringLockTicks--;
    }

    public void steerHorizontal(double desiredX, double desiredZ, double accel, boolean sprinting, float yawDegrees) {
        if (serverPlayer == null)
            return;

        Player bukkit = getBukkitPlayer();
        boolean grounded = bukkit != null && bukkit.isOnGround();

        Object velocity = M_GET_DELTA_MOVEMENT == null ? null : Reflect.invoke(serverPlayer, M_GET_DELTA_MOVEMENT);
        double currentX = getVecX(velocity);
        double currentY = getVecY(velocity);
        double currentZ = getVecZ(velocity);

        // Fake players do not always get a clean vanilla landing tick before we take
        // over again, so small negative Y values can accumulate and eventually push
        // them through the floor during fast horizontal movement.
        if (grounded && currentY < 0.0D) {
            currentY = 0.0D;
        }

        double nx, nz;
        boolean appliedKnockbackThisTick = false;

        // Blend the new hit into the current motion the same way vanilla hurt knockback
        // does:
        // halve horizontal speed once, then add the new horizontal impulse.
        if (injectedKnockbackX != 0 || injectedKnockbackZ != 0 || injectedKnockbackY != 0) {
            currentX = (currentX * 0.5D) + injectedKnockbackX;
            currentZ = (currentZ * 0.5D) + injectedKnockbackZ;
            if (injectedKnockbackY > 0.0D) {
                currentY = Math.max(currentY, injectedKnockbackY);
            } else if (injectedKnockbackY != 0.0D) {
                currentY += injectedKnockbackY;
            }
            appliedKnockbackThisTick = true;
            injectedKnockbackX = 0;
            injectedKnockbackY = 0;
            injectedKnockbackZ = 0;

            if (M_SET_DELTA_MOVEMENT_VEC != null) {
                Reflect.invoke(serverPlayer, M_SET_DELTA_MOVEMENT_VEC, newVec3(currentX, currentY, currentZ));
            }
        }

        if (steeringLockTicks > 0) {
            int lockTicksBeforeDecrement = steeringLockTicks;
            steeringLockTicks--;
            if (lockTicksBeforeDecrement >= 6) {
                debug("KB Applied -> initVel=(" + String.format("%.3f", currentX) + ", "
                        + String.format("%.3f", currentZ) + "), desired=(" + String.format("%.3f", desiredX) + ", "
                        + String.format("%.3f", desiredZ) + ")");
            }

            if (appliedKnockbackThisTick) {
                nx = currentX;
                nz = currentZ;
            } else {
                double friction = grounded ? 0.91D : 0.91D;
                double holdStrength = grounded ? 0.18D : 0.11D;
                if (knockbackInputBlendTicks > 0) {
                    holdStrength += grounded ? 0.08D : 0.05D;
                    knockbackInputBlendTicks--;
                }
                nx = (currentX * friction) + clamp(desiredX * holdStrength, -0.18D, 0.18D);
                nz = (currentZ * friction) + clamp(desiredZ * holdStrength, -0.18D, 0.18D);
            }

            // Keep feeding forward / strafe intent during knockback so the bot actually
            // W-holds.
            pushClientInput(desiredX, desiredZ, sprinting, yawDegrees);
        } else {
            if (knockbackInputBlendTicks > 0) {
                knockbackInputBlendTicks--;
            }
            nx = currentX + clamp((desiredX - currentX) * accel, -0.4D, 0.4D);
            nz = currentZ + clamp((desiredZ - currentZ) * accel, -0.4D, 0.4D);
        }

        double ny;
        if (appliedKnockbackThisTick) {
            // Do not immediately eat the launch with gravity on the exact impact tick.
            ny = currentY;
        } else if (grounded && currentY <= 0.0D) {
            ny = 0.0D;
        } else {
            ny = (currentY - 0.08D) * 0.98D;
            if (ny < -3.92D)
                ny = -3.92D;
        }

        if (M_SET_DELTA_MOVEMENT_VEC != null) {
            Reflect.invoke(serverPlayer, M_SET_DELTA_MOVEMENT_VEC, newVec3(nx, ny, nz));
        } else if (M_ADD_DELTA_MOVEMENT != null) {
            Reflect.invoke(serverPlayer, M_ADD_DELTA_MOVEMENT, newVec3(nx - currentX, ny - currentY, nz - currentZ));
        }

        moveSelf(new Vector(nx, ny, nz));
        flagImpulse();

        if (steeringLockTicks <= 0) {
            pushClientInput(desiredX, desiredZ, sprinting, yawDegrees);
        }
    }

    public void pushClientInput(double desiredX, double desiredZ, boolean sprinting, float yawDegrees) {
        if (serverPlayer == null || M_SET_LAST_CLIENT_INPUT == null || CTOR_INPUT == null)
            return;

        double yawRad = Math.toRadians(yawDegrees);
        Vector forward = new Vector(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vector right = new Vector(forward.getZ(), 0.0D, -forward.getX());
        Vector desired = new Vector(desiredX, 0.0D, desiredZ);

        double forwardDot = desired.dot(forward);
        double rightDot = desired.dot(right);
        double threshold = 0.01D;

        boolean forwardPressed = forwardDot > threshold;
        boolean backwardPressed = forwardDot < -threshold;
        boolean rightPressed = rightDot > threshold;
        boolean leftPressed = rightDot < -threshold;

        Object input = Reflect.newInstance(CTOR_INPUT,
                forwardPressed,
                backwardPressed,
                leftPressed,
                rightPressed,
                false,
                false,
                sprinting);
        Reflect.invoke(serverPlayer, M_SET_LAST_CLIENT_INPUT, input);
    }

    public void swingMainHand() {
        if (serverPlayer == null || M_SWING == null)
            return;
        Object mainHand = Reflect.enumValue(C_INTERACTION_HAND, "MAIN_HAND");
        Reflect.invoke(serverPlayer, M_SWING, mainHand);
    }

    public void attack(Object targetEntity) {
        if (serverPlayer == null || targetEntity == null || M_ATTACK == null) {
            debug("attack() aborted: serverPlayer=" + (serverPlayer != null) + ", target=" + (targetEntity != null)
                    + ", M_ATTACK=" + (M_ATTACK != null));
            return;
        }

        // The fake player's tick() may not advance the attack cooldown properly.
        // Reset the ticker so getAttackStrengthScale() returns 1.0 → full damage.
        if (M_RESET_ATTACK_STRENGTH != null) {
            Reflect.invoke(serverPlayer, M_RESET_ATTACK_STRENGTH);
        } else {
            debug("WARN: M_RESET_ATTACK_STRENGTH is null — attack may deal 0 damage!");
        }

        try {
            Reflect.invoke(serverPlayer, M_ATTACK, targetEntity);
            debug("attack() invoked successfully.");
        } catch (Exception ex) {
            debug("attack() threw: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    public void setBlocking(boolean blocking) {
        if (serverPlayer == null)
            return;
        Object offHand = Reflect.enumValue(C_INTERACTION_HAND, "OFF_HAND");
        if (blocking) {
            if (M_START_USING_ITEM != null)
                Reflect.invoke(serverPlayer, M_START_USING_ITEM, offHand);
        } else {
            if (M_RELEASE_USING_ITEM != null)
                Reflect.invoke(serverPlayer, M_RELEASE_USING_ITEM);
        }
    }

    public void setSprinting(boolean sprinting) {
        if (serverPlayer == null || M_SET_SPRINTING == null)
            return;
        Reflect.invoke(serverPlayer, M_SET_SPRINTING, sprinting);
    }

    public void resetMotion() {
        if (serverPlayer == null || M_SET_DELTA_MOVEMENT_VEC == null)
            return;
        Reflect.invoke(serverPlayer, M_SET_DELTA_MOVEMENT_VEC, newVec3(0.0D, 0.0D, 0.0D));
        flagImpulse();
    }

    /** Extract the delta movement and send it straight as a network packet. */
    private void flagImpulse() {
        if (serverPlayer == null || npcWrapper == null)
            return;
        try {
            Object vel = M_GET_DELTA_MOVEMENT == null ? null : Reflect.invoke(serverPlayer, M_GET_DELTA_MOVEMENT);
            if (vel != null) {
                npcWrapper.sendVelocitySync(getVecX(vel), getVecY(vel), getVecZ(vel));
            }
        } catch (Exception ex) {
            debug("flagImpulse strictly mapped packet failed: " + ex.getMessage());
        }
    }

    public void setItemSlot(String slotName, ItemStack stack) {
        if (serverPlayer == null || M_SET_ITEM_SLOT == null)
            return;
        Object slot = Reflect.enumValue(C_EQUIPMENT_SLOT, slotName);
        Object nmsStack = CraftItemStack.asNMSCopy(stack);
        Reflect.invoke(serverPlayer, M_SET_ITEM_SLOT, slot, nmsStack);
    }

    public Object newVec3(double x, double y, double z) {
        return Reflect.newInstance(CTOR_VEC3, x, y, z);
    }

    public double getVecX(Object vec) {
        return vec == null ? 0.0D : ((Number) Reflect.get(vec, F_VEC3_X)).doubleValue();
    }

    public double getVecY(Object vec) {
        return vec == null ? 0.0D : ((Number) Reflect.get(vec, F_VEC3_Y)).doubleValue();
    }

    public double getVecZ(Object vec) {
        return vec == null ? 0.0D : ((Number) Reflect.get(vec, F_VEC3_Z)).doubleValue();
    }

    public Object getNmsEntity(Player player) {
        if (player == null)
            return null;
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            getHandle.setAccessible(true);
            return getHandle.invoke(player);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Could not resolve CraftPlayer#getHandle", ex);
        }
    }

    public void moveSelf(Vector movement) {
        if (serverPlayer == null || movement == null || M_MOVE == null)
            return;
        Object moverType = Reflect.enumValue(C_MOVER_TYPE, "SELF");
        Reflect.invoke(serverPlayer, M_MOVE, moverType, newVec3(movement.getX(), movement.getY(), movement.getZ()));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
