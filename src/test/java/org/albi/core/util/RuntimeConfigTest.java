package org.albi.core.util;

import org.albi.core.modules.Duels.DuelSettings;
import org.albi.core.modules.Duels.ranked.RankedRules;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigTest {
    @Test
    void olderConfigurationsReceiveTheSameDefaultsAsTheBundledFile() throws Exception {
        var older = new YamlConfiguration();
        var bundled = new YamlConfiguration();
        try (var input = getClass().getResourceAsStream("/config.yml");
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            bundled.load(reader);
        }
        // Paper resolves material metadata through a live registry. Compare those defaults by name here.
        assertEquals(LobbySettings.from(older).interaction().allowedItems(),
                bundled.getStringList("lobby.interaction.allowed_items").stream()
                        .map(Material::valueOf).collect(Collectors.toSet()));
        var food = bundled.getConfigurationSection("combat.food_cooldowns");
        assertNotNull(food);
        var cooldowns = food.getKeys(false).stream().collect(Collectors.toMap(
                Material::valueOf, name -> food.getInt(name) * 1_000L));
        assertEquals(CombatSettings.from(older).foodCooldowns(), cooldowns);
        bundled.set("lobby.interaction.allowed_items", null);
        bundled.set("combat.food_cooldowns", null);

        assertEquals(LobbySettings.from(bundled), LobbySettings.from(older));
        assertEquals(DuelSettings.from(bundled), DuelSettings.from(older));
        assertEquals(CombatSettings.from(bundled), CombatSettings.from(older));
        assertEquals(RankedRules.from(bundled), RankedRules.from(older));
    }

    @Test
    void invalidValuesFallBackAndExplicitZeroOrEmptyValuesAreRespected() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
                lobby:
                  hotbar:
                    cooldown_seconds: .nan
                  movement:
                    speed_level: -2
                    jump_boost_level: 0
                  interaction:
                    allowed_items: []
                duels:
                  rounds: [0, 7, 7, 150, 4.25]
                  countdown:
                    start_seconds: 0
                combat:
                  food_cooldowns: {}
                  kit_damage_boosts:
                    - {kit: smp.pot, percent: 0}
                """);

        var lobby = LobbySettings.from(config);
        assertEquals(1, lobby.equipment().shortcutCooldown());
        assertEquals(2, lobby.equipment().speedLevel());
        assertEquals(0, lobby.equipment().jumpBoostLevel());
        assertEquals(Set.of(), lobby.interaction().allowedItems());

        var duels = DuelSettings.from(config);
        assertEquals(List.of(7), duels.rounds());
        assertEquals(0, duels.countdown().startSeconds());

        var combat = CombatSettings.from(config);
        assertTrue(combat.foodCooldowns().isEmpty());
        assertEquals(0, combat.damageBoost("SMP.POT", 33));
        assertEquals(33, combat.damageBoost("other", 33));
        assertEquals(10_000L, CombatSettings.from(new YamlConfiguration()).foodCooldowns().get(Material.RAW_GOLD));
    }
}
