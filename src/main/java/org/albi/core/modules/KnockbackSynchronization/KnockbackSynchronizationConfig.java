package org.albi.core.modules.KnockbackSynchronization;

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

public final class KnockbackSynchronizationConfig {

    private static final String RESOURCE_NAME = "knockback-synchronization.yml";

    private final Core plugin;
    private final File file;

    private FileConfiguration configuration;

    private boolean enabled;
    private boolean runnableEnabled;
    private boolean offGroundSynchronization;
    private boolean debug;
    private long sampleIntervalTicks;
    private long combatTimeoutTicks;
    private double pingOffset;
    private double spikeThreshold;
    private double maxGroundDistance;
    private double maxRayDistance;
    private int maxNoDamageTicks;
    private double tickRate;
    private List<String> exemptWorlds;

    public KnockbackSynchronizationConfig(Core plugin) {
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
            throw new RuntimeException("Failed to create default knockback synchronization config", ex);
        }
    }

    public void reload() {
        this.configuration = YamlConfiguration.loadConfiguration(file);
        this.enabled = configuration.getBoolean("enabled", true);
        this.runnableEnabled = configuration.getBoolean("runnable.enabled", true);
        this.offGroundSynchronization = configuration.getBoolean(
                "enable_offground_synchronization",
                configuration.getBoolean("sync.off_ground.enabled", false)
        );
        this.debug = configuration.getBoolean("debug", false);
        this.sampleIntervalTicks = Math.max(1L, configuration.getLong(
                "runnable.interval",
                configuration.getLong("combat.sample_interval_ticks", 5L)
        ));
        this.combatTimeoutTicks = Math.max(1L, configuration.getLong(
                "runnable.combat_timer",
                configuration.getLong("combat.timeout_ticks", 30L)
        ));
        this.pingOffset = Math.max(0.0D, configuration.getDouble(
                "ping_offset",
                configuration.getDouble("ping.offset_ms", 25.0D)
        ));
        this.spikeThreshold = Math.max(0.0D, configuration.getDouble(
                "spike_threshold",
                configuration.getDouble("ping.spike_threshold_ms", 20.0D)
        ));
        this.maxGroundDistance = Math.max(0.01D, configuration.getDouble("sync.max_ground_distance", 1.3D));
        this.maxRayDistance = Math.max(0.5D, configuration.getDouble("sync.max_ray_distance", 5.0D));
        this.maxNoDamageTicks = Math.max(0, configuration.getInt("sync.max_no_damage_ticks", 8));
        this.tickRate = Math.max(1.0D, configuration.getDouble("sync.tick_rate", 20.0D));
        this.exemptWorlds = Collections.unmodifiableList(configuration.getStringList("exempt_worlds"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRunnableEnabled() {
        return runnableEnabled;
    }

    public boolean isOffGroundSynchronization() {
        return offGroundSynchronization;
    }

    public boolean isDebug() {
        return debug;
    }

    public long getSampleIntervalTicks() {
        return sampleIntervalTicks;
    }

    public long getCombatTimeoutTicks() {
        return combatTimeoutTicks;
    }

    public double getPingOffset() {
        return pingOffset;
    }

    public double getSpikeThreshold() {
        return spikeThreshold;
    }

    public double getMaxGroundDistance() {
        return maxGroundDistance;
    }

    public double getMaxRayDistance() {
        return maxRayDistance;
    }

    public int getMaxNoDamageTicks() {
        return maxNoDamageTicks;
    }

    public double getTickRate() {
        return tickRate;
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
