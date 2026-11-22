package org.albi.core.events;

import org.albi.core.Core;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OnServerLoaded implements Listener {
    private static final Map<String, Object> LOBBY_RULES = Map.of(
            "doWeatherCycle", false, "doMobSpawning", false, "announceAdvancements", false);
    private static final Map<String, Object> OTHER_RULES = Map.of(
            "keepInventory", true, "doImmediateRespawn", true, "doDaylightCycle", false,
            "doWeatherCycle", false, "doMobSpawning", false, "announceAdvancements", false);

    private final Core plugin;

    public OnServerLoaded(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (plugin.getConfig().getBoolean("world_rules.enabled", true)) Bukkit.getWorlds().forEach(this::apply);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (plugin.getConfig().getBoolean("world_rules.enabled", true)
                && plugin.getConfig().getBoolean("world_rules.apply_on_world_load", false)) apply(event.getWorld());
    }

    private void apply(World world) {
        var config = plugin.getConfig();
        boolean lobby = world.getName().equalsIgnoreCase(config.getString("lobby_world", "world"));
        var profile = config.getConfigurationSection(lobby ? "world_rules.lobby" : "world_rules.other");
        var override = config.getConfigurationSection("world_rules.worlds." + world.getName());
        if (override != null && !override.getBoolean("enabled", true)) return;

        Map<String, Object> rules = new LinkedHashMap<>();
        String difficulty = null;
        if ((override == null || override.getBoolean("inherit", true))
                && (profile == null || profile.getBoolean("enabled", true))) {
            if (profile == null || profile.getBoolean("inherit_defaults", true)) {
                rules.putAll(lobby ? LOBBY_RULES : OTHER_RULES);
                if (!lobby) difficulty = "HARD";
            }
            if (profile != null) difficulty = profile.getString("difficulty", difficulty);
            addRules(rules, profile);
        }
        if (override != null) difficulty = override.getString("difficulty", difficulty);
        addRules(rules, override);
        setDifficulty(world, difficulty);
        rules.forEach((name, value) -> setRule(world, name, value));
    }

    private static void addRules(Map<String, Object> rules, ConfigurationSection profile) {
        if (profile == null) return;
        var configured = profile.getConfigurationSection("gamerules");
        if (configured != null) configured.getValues(false).forEach((name, value) -> {
            if ("inherit".equals(value)) rules.remove(name);
            else rules.put(name, value);
        });
    }

    private void setDifficulty(World world, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("inherit")) return;
        try {
            world.setDifficulty(Difficulty.valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning("[World rules] Unknown difficulty for " + world.getName());
        }
    }

    private void setRule(World world, String name, Object value) {
        GameRule<?> rule = GameRule.getByName(name);
        if (rule == null || !rule.getType().isInstance(value)) {
            plugin.getLogger().warning("[World rules] Invalid gamerule " + name + " for " + world.getName());
            return;
        }
        applyRule(world, rule, value);
    }

    private static <T> void applyRule(World world, GameRule<T> rule, Object value) {
        world.setGameRule(rule, rule.getType().cast(value));
    }
}
