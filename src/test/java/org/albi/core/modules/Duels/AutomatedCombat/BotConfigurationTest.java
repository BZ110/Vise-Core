package org.albi.core.modules.Duels.AutomatedCombat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BotConfigurationTest {
    @Test
    void practiceSessionCopiesValidatedServerDefaults() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
                name: Practice Trainer
                reach_distance: .nan
                move_speed: 0.4
                invulnerability: false
                """);

        var settings = new BotSettings(List.of(), 1, false);
        settings.applyConfiguredDefaults(config);
        var session = settings.copy();

        assertEquals("Practice Trainer", session.getBotNameFor("someone"));
        assertEquals(0.4, session.getMoveSpeed());
        assertEquals(3.1, session.getReachDistance());
        assertFalse(session.isInvulnerability());
    }
}
