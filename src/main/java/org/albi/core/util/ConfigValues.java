package org.albi.core.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class ConfigValues {
    private ConfigValues() {}

    public static int integer(ConfigurationSection config, String path, int fallback, int min, int max) {
        Object value = config.get(path);
        if (!(value instanceof Number number)) return fallback;
        double amount = number.doubleValue();
        return amount >= min && amount <= max && amount == Math.rint(amount) ? (int) amount : fallback;
    }

    public static double decimal(ConfigurationSection config, String path, double fallback, double min, double max) {
        Object value = config.get(path);
        if (!(value instanceof Number number)) return fallback;
        double amount = number.doubleValue();
        return amount >= min && amount <= max ? amount : fallback;
    }

    public static long seconds(ConfigurationSection config, String path, int fallback, int min, int max) {
        return integer(config, path, fallback, min, max) * 1_000L;
    }

    public static List<Integer> integers(ConfigurationSection config, String path, List<Integer> defaults, int min, int max) {
        if (!config.isList(path)) return defaults;
        var raw = config.getList(path);
        var valid = raw.stream().filter(Number.class::isInstance).map(Number.class::cast).mapToDouble(Number::doubleValue)
                .filter(value -> value >= min && value <= max && value == Math.rint(value))
                .mapToObj(value -> (int) value).distinct().toList();
        return valid.isEmpty() && !raw.isEmpty() ? defaults : valid;
    }

    public static Set<Material> materials(ConfigurationSection config, String path, Collection<Material> defaults) {
        if (!config.isList(path)) return Set.copyOf(defaults);
        Set<Material> result = EnumSet.noneOf(Material.class);
        var raw = config.getList(path);
        for (Object value : raw) {
            if (!(value instanceof String name)) continue;
            Material material = Material.matchMaterial(name);
            if (material != null && !material.isAir()) result.add(material);
        }
        return result.isEmpty() && !raw.isEmpty() ? Set.copyOf(defaults) : Set.copyOf(result);
    }

    /** Environment variables override YAML, including when an empty value disables an integration. */
    public static String envOrYaml(ConfigurationSection config, String path, String variable) {
        String value = System.getenv(variable);
        return value == null ? config.getString(path, "") : value;
    }

    /** Return null for disabled or invalid HTTP endpoints without logging potentially secret URLs. */
    public static URI httpEndpoint(String value) {
        try {
            URI uri = URI.create(value.strip());
            String scheme = uri.getScheme();
            if (("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && uri.getUserInfo() == null) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) {
            // Operators can correct the setting in YAML or the environment.
        }
        return null;
    }
}
