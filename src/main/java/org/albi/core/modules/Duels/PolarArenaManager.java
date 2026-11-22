package org.albi.core.modules.Duels;

import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.PolarWorldAccess;
import live.minehub.polarpaper.PolarWriter;
import live.minehub.polarpaper.source.FilePolarSource;
import org.albi.core.Core;
import org.albi.core.modules.Duels.helpers.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PolarArenaManager {
    private static final byte DEFAULT_MIN_SECTION = -4;
    private static final byte DEFAULT_MAX_SECTION = 19;

    private final Core plugin;

    public PolarArenaManager(Core plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("polarpaper") != null;
    }

    private String originWorldName(Arena arena) {
        return arena.getName().toLowerCase(Locale.ROOT) + "-p-orig";
    }

    public String nextRuntimeWorldName(Arena arena) {
        String base = arena.getName() == null ? "arena" : arena.getName().toLowerCase(Locale.ROOT).replace(' ', '-');
        return base + "-p-inst-" + UUID.randomUUID().toString().substring(0, 8) + "-duel";
    }

    public Path originPath(Arena arena) {
        return FilePolarSource.defaultFolder(originWorldName(arena)).path();
    }

    public Arena createOrConvertPolarArena(String name) {
        Arena arena = DuelManager.stor.getArenaByName(name);
        if (arena == null) {
            arena = new Arena();
            arena.setName(name);
            DuelManager.stor.registerArena(arena);
        }
        arena.setPolarArena(true);
        return arena;
    }

    public CompletableFuture<World> createOrLoadOriginWorld(Arena arena) {
        ensureOriginFile(arena);
        String worldName = originWorldName(arena);
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) return CompletableFuture.completedFuture(existing);
        return Polar.loadWorld(new FilePolarSource(originPath(arena)), worldName, buildOriginConfig(arena), PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    public CompletableFuture<World> cloneRuntimeWorld(Arena arena, String worldName) {
        ensureOriginFile(arena);
        return Polar.loadWorld(new FilePolarSource(originPath(arena)), worldName, buildRuntimeConfig(arena), PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    public void saveOriginWorld(Arena arena) {
        World world = Bukkit.getWorld(originWorldName(arena));
        if (world == null) return;
        Polar.saveWorld(world, new FilePolarSource(originPath(arena)));
    }

    public void saveOriginWorld(World world, Arena arena) {
        if (world == null) return;
        Polar.saveWorld(world, new FilePolarSource(originPath(arena)));
    }

    public void unloadWorld(String worldName, boolean deleteTempFolder) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        if (deleteTempFolder) {
            deleteWorldFolder(tempFolder().resolve(worldName));
        }
    }

    public void unloadWorldLater(String worldName, boolean deleteTempFolder, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> unloadWorld(worldName, deleteTempFolder), delayTicks);
    }

    private void ensureOriginFile(Arena arena) {
        Path path = originPath(arena);
        if (Files.exists(path)) return;
        try {
            Files.createDirectories(path.getParent());
            Config config = buildOriginConfig(arena);
            PolarWorld blank = new PolarWorld(DEFAULT_MIN_SECTION, DEFAULT_MAX_SECTION, config);
            byte[] bytes = PolarWriter.write(blank);
            Files.write(path, bytes);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create polar origin file for arena " + arena.getName(), ex);
        }
    }

    private Config buildOriginConfig(Arena arena) {
        return Config.BLANK_DEFAULT.toBuilder()
                .spawn(new Location(null, 0, 64, 0))
                .difficulty(Difficulty.NORMAL)
                .worldType(WorldType.FLAT)
                .saveOnStop(true)
                .loadOnStartup(false)
                .async(false)
                .removeChunks(false)
                .build();
    }

    private Config buildRuntimeConfig(Arena arena) {
        return Config.BLANK_DEFAULT.toBuilder()
                .spawn(new Location(null, 0, 64, 0))
                .difficulty(Difficulty.NORMAL)
                .worldType(WorldType.FLAT)
                .saveOnStop(false)
                .loadOnStartup(false)
                .async(false)
                .removeChunks(true)
                .build();
    }

    private Path tempFolder() {
        return plugin.getServer().getPluginManager().getPlugin("polarpaper").getDataFolder().toPath().resolve("temp");
    }

    private void deleteWorldFolder(Path path) {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
