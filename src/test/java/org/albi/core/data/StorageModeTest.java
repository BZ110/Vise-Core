package org.albi.core.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StorageModeTest {
    @Test
    void freshConfigIsStandaloneAndOlderConfigsRetainRedis() throws Exception {
        var bundled = new YamlConfiguration();
        try (var file = getClass().getResourceAsStream("/config.yml");
             var reader = new InputStreamReader(file, StandardCharsets.UTF_8)) {
            bundled.load(reader);
        }
        assertFalse(StatManagement.Config.from(bundled).redisEnabled());

        var older = new YamlConfiguration();
        older.set("mysql.host", "localhost");
        older.set("mysql.database", "vise_core");
        older.set("mysql.user", "vise_core");
        older.set("redis.host", "localhost");
        older.setDefaults(bundled);
        assertTrue(StatManagement.Config.from(older).redisEnabled());
        assertFalse(StatManagement.Config.from(older).redisFallbackToMysql());
    }

    @Test
    void disabledRedisSkipsEndpointValidationAndExperimentalModeIsOptIn() {
        var yaml = new YamlConfiguration();
        yaml.set("mysql.host", "localhost");
        yaml.set("mysql.database", "vise_core");
        yaml.set("mysql.user", "vise_core");
        yaml.set("redis.enabled", false);
        yaml.set("redis.host", "");
        yaml.set("redis.port", -1);
        yaml.set("storage.experimental_sql_shared_state", true);
        var settings = StatManagement.Config.from(yaml);
        assertFalse(settings.redisEnabled());
        assertTrue(settings.sqlSharedState());
        assertFalse(settings.redisFallbackToMysql());
    }
}
