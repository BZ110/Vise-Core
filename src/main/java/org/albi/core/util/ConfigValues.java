package org.albi.core.util;

import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;

public final class ConfigValues {
    private ConfigValues() {}

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
