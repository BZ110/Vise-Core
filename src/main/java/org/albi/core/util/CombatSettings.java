package org.albi.core.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.albi.core.util.ConfigValues.integer;

public record CombatSettings(Map<Material, Long> foodCooldowns, boolean customCreeperEggs, boolean consumeCreeperEgg,
                             int creeperFuseTicks, int legacyBoostPercent, Map<String, Integer> kitDamageBoosts) {
    public static CombatSettings from(ConfigurationSection config) {
        var foods = config.getConfigurationSection("combat.food_cooldowns");
        Map<Material, Long> cooldowns = new HashMap<>();
        if (foods == null) {
            cooldowns.put(Material.RAW_GOLD, 10_000L);
        } else {
            for (String name : foods.getKeys(false)) {
                Material material = Material.matchMaterial(name);
                int seconds = integer(foods, name, -1, 0, 3600);
                if (material != null && material.isItem() && seconds >= 0) cooldowns.put(material, seconds * 1000L);
            }
        }
        Map<String, Integer> boosts = new HashMap<>();
        for (Map<?, ?> entry : config.getMapList("combat.kit_damage_boosts")) {
            if (!(entry.get("kit") instanceof String kit) || kit.isBlank()) continue;
            if (!(entry.get("percent") instanceof Number percent)) continue;
            double value = percent.doubleValue();
            if (value >= 0 && value <= 500 && value == Math.rint(value)) {
                boosts.put(kit.strip().toLowerCase(Locale.ROOT), (int) value);
            }
        }

        return new CombatSettings(Map.copyOf(cooldowns), config.getBoolean("combat.creeper_eggs.enabled", true),
                config.getBoolean("combat.creeper_eggs.consume", true),
                integer(config, "combat.creeper_eggs.fuse_ticks", 20, 1, 1200),
                integer(config, "combat.legacy_damage_boost_percent", 33, 0, 500), Map.copyOf(boosts));
    }

    public int damageBoost(String kit, int fallback) {
        return kit == null ? fallback : kitDamageBoosts.getOrDefault(kit.toLowerCase(Locale.ROOT), fallback);
    }
}
