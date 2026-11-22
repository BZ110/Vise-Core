package org.albi.core.gui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Loads both files before publishing a new snapshot to menus and message senders. */
public final class InterfaceFiles {
    private record Snapshot(YamlConfiguration lang, YamlConfiguration defaultLang, YamlConfiguration gui,
                            ConfigurationSection legacyLang) {}

    private final JavaPlugin plugin;
    private volatile Snapshot snapshot = new Snapshot(new YamlConfiguration(), new YamlConfiguration(), new YamlConfiguration(), null);

    public InterfaceFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(boolean startup) throws IOException, InvalidConfigurationException {
        var defaultsLang = bundled("lang.yml");
        var defaultsGui = bundled("gui.yml");
        var legacy = plugin.getConfig().getConfigurationSection("lang");

        if (startup) snapshot = new Snapshot(new YamlConfiguration(), defaultsLang, defaultsGui, legacy);

        var langPath = new File(plugin.getDataFolder(), "lang.yml");
        if (!langPath.exists()) createLanguageFile(langPath, legacy);
        var guiPath = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiPath.exists()) plugin.saveResource("gui.yml", false);

        var lang = readOverride(langPath);
        var gui = readOverride(guiPath);
        requireSection(lang, "lang.yml", "messages");
        requireSection(gui, "gui.yml", "menus");
        requireSection(gui, "gui.yml", "text");
        snapshot = new Snapshot(lang, defaultsLang, mergeMenus(defaultsGui, gui), legacy);
    }

    static YamlConfiguration mergeMenus(ConfigurationSection bundled, ConfigurationSection overrides) {
        var effective = new YamlConfiguration();
        copyValues(bundled, effective);
        copyValues(overrides, effective);
        return effective;
    }

    private static void copyValues(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection child) {
                var existing = target.getConfigurationSection(key);
                copyValues(child, existing == null ? target.createSection(key) : existing);
            } else {
                target.set(key, value);
            }
        }
    }

    private void createLanguageFile(File destination, ConfigurationSection legacy)
            throws IOException, InvalidConfigurationException {
        if (legacy == null || legacy.getKeys(true).isEmpty()) {
            plugin.saveResource("lang.yml", false);
            return;
        }

        var migrated = bundled("lang.yml");
        legacy.getValues(true).forEach((key, value) -> {
            if (value instanceof String) migrated.set(key, value);
        });
        var pending = Files.createTempFile(destination.toPath().getParent(), "lang-", ".yml.tmp");
        try {
            migrated.save(pending.toFile());
            try {
                Files.move(pending, destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, destination.toPath());
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    private static void requireSection(YamlConfiguration config, String file, String key) throws InvalidConfigurationException {
        if (config.isSet(key) && !config.isConfigurationSection(key)) {
            throw new InvalidConfigurationException(file + " '" + key + "' must be a section");
        }
    }

    private YamlConfiguration bundled(String name) throws IOException, InvalidConfigurationException {
        var bundled = new YamlConfiguration();
        try (var input = Objects.requireNonNull(plugin.getResource(name), name);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            bundled.load(reader);
        }
        return bundled;
    }

    private static YamlConfiguration readOverride(File file)
            throws IOException, InvalidConfigurationException {
        var config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (InvalidConfigurationException e) {
            throw new InvalidConfigurationException(file.getName() + " could not be parsed", e);
        }
        return config;
    }

    public String message(String key) {
        Snapshot current = snapshot;
        if (current.lang.isString(key)) return current.lang.getString(key);
        if (current.legacyLang != null && current.legacyLang.isString(key)) return current.legacyLang.getString(key);
        return current.defaultLang.getString(key);
    }

    public YamlConfiguration gui() {
        return snapshot.gui;
    }
}
