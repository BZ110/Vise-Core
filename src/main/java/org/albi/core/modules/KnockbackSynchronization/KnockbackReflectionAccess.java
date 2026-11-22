package org.albi.core.modules.KnockbackSynchronization;

import org.albi.mods.Reflect;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class KnockbackReflectionAccess {

    private static final Method M_GET_PING = Reflect.resolveMethod(Player.class, new String[] { "getPing" });

    private static final Attribute GRAVITY = enumAttribute("GRAVITY");
    private static final Attribute KNOCKBACK_RESISTANCE = enumAttribute("KNOCKBACK_RESISTANCE");

    private KnockbackReflectionAccess() {
    }

    public static int getPing(Player player) {
        Integer ping = Reflect.invoke(player, M_GET_PING);
        return ping == null ? 0 : ping;
    }

    public static double getGravity(Player player, double fallback) {
        return getAttributeValue(player, GRAVITY, fallback);
    }

    public static double getKnockbackResistance(Player player, double fallback) {
        return getAttributeValue(player, KNOCKBACK_RESISTANCE, fallback);
    }

    private static double getAttributeValue(Player player, Attribute attribute, double fallback) {
        if (attribute == null) {
            return fallback;
        }

        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private static Attribute enumAttribute(String name) {
        try {
            return (Attribute) Reflect.enumValue(Attribute.class, name);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
