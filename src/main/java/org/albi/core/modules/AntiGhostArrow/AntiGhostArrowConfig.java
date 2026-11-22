package org.albi.core.modules.AntiGhostArrow;

import org.albi.core.Core;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

public final class AntiGhostArrowConfig {

    private static final String RESOURCE_NAME = "anti-ghost-arrow.yml";

    private final Core plugin;
    private final File file;

    private FileConfiguration configuration;

    private boolean enabled;
    private boolean debug;
    private double hitboxExpansion;
    private double segmentExpansion;
    private double minimumTravelDistance;
    private long ownerGraceTicks;
    private int subSteps;
    private double relativeVelocityExpansion;
    private boolean removeArrowOnForcedHit;
    private long maxTrackedTicks;
    private boolean ignorePlayersWithNoDamageTicks;
    private boolean ignoreCreativePlayers;
    private boolean ignoreSpectators;
    private List<String> exemptWorlds;

    public AntiGhostArrowConfig(Core plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), RESOURCE_NAME);
        ensureDefaultExists();
        reload();
    }

    private void ensureDefaultExists() {
        if (file.exists()) {
            return;
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream input = plugin.getResource(RESOURCE_NAME)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled resource: " + RESOURCE_NAME);
            }
            Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create default anti-ghost-arrow config", ex);
        }
    }

    public void reload() {
        this.configuration = YamlConfiguration.loadConfiguration(file);
        this.enabled = configuration.getBoolean("enabled", true);
        this.debug = configuration.getBoolean("debug", false);
        this.hitboxExpansion = Math.max(0.0D, configuration.getDouble("collision.hitbox_expansion", 0.12D));
        this.segmentExpansion = Math.max(0.0D, configuration.getDouble("collision.segment_expansion", 0.20D));
        this.minimumTravelDistance = Math.max(0.0D, configuration.getDouble("collision.minimum_travel_distance", 0.05D));
        this.ownerGraceTicks = Math.max(0L, configuration.getLong("collision.owner_grace_ticks", 5L));
        this.subSteps = Math.max(1, configuration.getInt("collision.sub_steps", 4));
        this.relativeVelocityExpansion = Math.max(0.0D, configuration.getDouble("collision.relative_velocity_expansion", 0.30D));
        this.removeArrowOnForcedHit = configuration.getBoolean("collision.remove_arrow_on_forced_hit", true);
        this.maxTrackedTicks = Math.max(1L, configuration.getLong("tracking.max_tracked_ticks", 200L));
        this.ignorePlayersWithNoDamageTicks = configuration.getBoolean("targets.ignore_players_with_no_damage_ticks", true);
        this.ignoreCreativePlayers = configuration.getBoolean("targets.ignore_creative_players", true);
        this.ignoreSpectators = configuration.getBoolean("targets.ignore_spectators", true);
        this.exemptWorlds = Collections.unmodifiableList(configuration.getStringList("exempt_worlds"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebug() {
        return debug;
    }

    public double getHitboxExpansion() {
        return hitboxExpansion;
    }

    public double getSegmentExpansion() {
        return segmentExpansion;
    }

    public double getMinimumTravelDistance() {
        return minimumTravelDistance;
    }

    public long getOwnerGraceTicks() {
        return ownerGraceTicks;
    }

    public int getSubSteps() {
        return subSteps;
    }

    public double getRelativeVelocityExpansion() {
        return relativeVelocityExpansion;
    }

    public boolean isRemoveArrowOnForcedHit() {
        return removeArrowOnForcedHit;
    }

    public long getMaxTrackedTicks() {
        return maxTrackedTicks;
    }

    public boolean isIgnorePlayersWithNoDamageTicks() {
        return ignorePlayersWithNoDamageTicks;
    }

    public boolean isIgnoreCreativePlayers() {
        return ignoreCreativePlayers;
    }

    public boolean isIgnoreSpectators() {
        return ignoreSpectators;
    }

    public List<String> getExemptWorlds() {
        return exemptWorlds;
    }

    public FileConfiguration getConfiguration() {
        return configuration;
    }

    public File getFile() {
        return file;
    }
}
