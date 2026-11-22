package org.albi.core.modules.AntiGhostArrow;

import org.albi.mods.Reflect;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiGhostArrowReflectionAccess {

    private static final Class<?> C_NMS_ENTITY = resolveClassAny(
            "net.minecraft.world.entity.Entity"
    );
    private static final Class<?> C_NMS_ABSTRACT_ARROW = resolveClassAny(
            "net.minecraft.world.entity.projectile.AbstractArrow",
            "net.minecraft.world.entity.projectile.arrow.AbstractArrow"
    );
    private static final Class<?> C_VEC3 = resolveClassAny(
            "net.minecraft.world.phys.Vec3"
    );
    private static final Class<?> C_ENTITY_HIT_RESULT = resolveClassAny(
            "net.minecraft.world.phys.EntityHitResult"
    );

    private static final Constructor<?> CTOR_VEC3 = C_VEC3 == null
            ? null
            : Reflect.resolveConstructor(C_VEC3, double.class, double.class, double.class);
    private static final Constructor<?> CTOR_ENTITY_HIT_RESULT_WITH_POS = C_ENTITY_HIT_RESULT == null || C_NMS_ENTITY == null || C_VEC3 == null
            ? null
            : Reflect.resolveConstructor(C_ENTITY_HIT_RESULT, C_NMS_ENTITY, C_VEC3);
    private static final Constructor<?> CTOR_ENTITY_HIT_RESULT = C_ENTITY_HIT_RESULT == null || C_NMS_ENTITY == null
            ? null
            : Reflect.resolveConstructor(C_ENTITY_HIT_RESULT, C_NMS_ENTITY);

    private static final Method M_ON_HIT_ENTITY = C_NMS_ABSTRACT_ARROW == null || C_ENTITY_HIT_RESULT == null
            ? null
            : Reflect.resolveMethod(C_NMS_ABSTRACT_ARROW, new String[] { "onHitEntity", "onEntityHit" }, C_ENTITY_HIT_RESULT);

    private static final Method M_DISCARD = C_NMS_ENTITY == null
            ? null
            : Reflect.resolveMethod(C_NMS_ENTITY, new String[] { "discard" });

    private static final Field F_IN_GROUND = C_NMS_ABSTRACT_ARROW == null
            ? null
            : Reflect.resolveField(C_NMS_ABSTRACT_ARROW, "inGround", "d");

    private static final Map<Class<?>, Method> HANDLE_METHODS = new ConcurrentHashMap<>();

    private AntiGhostArrowReflectionAccess() {
    }

    public static boolean isSupported() {
        return C_NMS_ENTITY != null
                && C_NMS_ABSTRACT_ARROW != null
                && C_VEC3 != null
                && C_ENTITY_HIT_RESULT != null
                && (CTOR_ENTITY_HIT_RESULT_WITH_POS != null || CTOR_ENTITY_HIT_RESULT != null)
                && M_ON_HIT_ENTITY != null;
    }

    public static void forceHit(AbstractArrow arrow, Entity target, Vector hitPosition) {
        Object arrowHandle = getHandle(arrow);
        Object targetHandle = getHandle(target);
        Object hitResult = createEntityHitResult(targetHandle, hitPosition);
        Reflect.invoke(arrowHandle, M_ON_HIT_ENTITY, hitResult);
    }

    /**
     * Marks the arrow as "used" by setting its NMS inGround flag and
     * calling discard() to remove it from the world cleanly.
     * This prevents vanilla physics from processing the arrow further
     * (which causes the visual "bounce" artifact).
     */
    public static void discardArrow(AbstractArrow arrow) {
        Object arrowHandle = getHandle(arrow);

        // Set inGround to true so vanilla tick() skips collision processing
        if (F_IN_GROUND != null) {
            try {
                Reflect.set(arrowHandle, F_IN_GROUND, true);
            } catch (Throwable ignored) {
                // Non-critical; the remove() call below will still clean up
            }
        }

        // Discard via NMS for clean removal
        if (M_DISCARD != null) {
            try {
                Reflect.invoke(arrowHandle, M_DISCARD);
            } catch (Throwable ignored) {
                // Fallback to Bukkit remove
                arrow.remove();
            }
        } else {
            arrow.remove();
        }
    }

    private static Object createEntityHitResult(Object targetHandle, Vector hitPosition) {
        if (CTOR_ENTITY_HIT_RESULT_WITH_POS != null && hitPosition != null && CTOR_VEC3 != null) {
            Object vec3 = Reflect.newInstance(CTOR_VEC3, hitPosition.getX(), hitPosition.getY(), hitPosition.getZ());
            return Reflect.newInstance(CTOR_ENTITY_HIT_RESULT_WITH_POS, targetHandle, vec3);
        }
        return Reflect.newInstance(CTOR_ENTITY_HIT_RESULT, targetHandle);
    }

    private static Object getHandle(Object bukkitEntity) {
        Method method = HANDLE_METHODS.computeIfAbsent(bukkitEntity.getClass(), type ->
                Reflect.resolveMethod(type, new String[] { "getHandle" }));
        if (method == null) {
            throw new IllegalStateException("Could not resolve getHandle for " + bukkitEntity.getClass().getName());
        }
        return Reflect.invoke(bukkitEntity, method);
    }

    private static Class<?> resolveClassAny(String... names) {
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }
}
