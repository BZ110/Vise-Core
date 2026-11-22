package org.albi.core.modules.Duels.polar;

import org.albi.core.gui.Messages;

import org.albi.core.Core;
import org.albi.core.modules.Duels.DuelManager;
import org.albi.core.modules.Duels.helpers.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;

public final class PolarArenaManager {

    private final Core plugin;
    private final PolarQueue queue;
    private final PolarBackend backend;

    private final Map<String, Deque<PolarArenaReservation>> available = new HashMap<>();
    private final Map<String, Set<PolarArenaReservation>> inUse = new HashMap<>();
    private final Map<String, Integer> nextInstanceIds = new HashMap<>();
    private final Set<String> warmQueued = new HashSet<>();
    private final Set<PolarArenaReservation> unusedQueueCopies = new HashSet<>();

    // NEW: cache the origin .polar bytes in memory
    private final Map<String, byte[]> cachedOriginBytes = new HashMap<>();

    public PolarArenaManager(Core plugin) {
        this.plugin = plugin;
        this.queue = new PolarQueue(plugin);
        this.backend = loadBackend(plugin);
    }

    private static PolarBackend loadBackend(Core plugin) {
        if (!plugin.getConfig().getBoolean("use_polar_worlds")
                || !plugin.getServer().getPluginManager().isPluginEnabled("polarpaper")) return null;
        try {
            return new PolarBackend();
        } catch (NoSuchMethodException | LinkageError failure) {
            plugin.getLogger().warning("[PolarArena] Incompatible PolarPaper API (" + failure.getClass().getSimpleName() + ").");
            return null;
        }
    }

    public boolean isEnabled() {
        return backend != null;
    }

    public boolean isPolarArena(Arena arena) {
        return isEnabled() && arena != null && arena.isPolarArena();
    }

    public int getPreloadCount() {
        return Math.max(1, plugin.getConfig().getInt("polar_preload", 2));
    }

    public void bootstrapStoredArenas() {
        if (!isEnabled() || DuelManager.stor == null)
            return;

        for (Arena arena : DuelManager.stor.arenas) {
            if (!isPolarArena(arena))
                continue;
            ensureArenaMaps(arena);
            warmArenaNow(arena);
        }
    }

    public void createArena(Arena arena, Player creator, Consumer<Boolean> callback) {
        if (!isPolarArena(arena)) {
            callback.accept(false);
            return;
        }
        ensureArenaMaps(arena);

        queue.enqueue("polar-create-" + arena.getName(), done -> {
            boolean ok = false;
            try {
                String originName = originWorldName(arena);
                Files.createDirectories(originPath(arena).getParent());

                World loadedOrigin = Bukkit.getWorld(originName);
                if (loadedOrigin == null) {
                    if (!Files.exists(originPath(arena))) {
                        createBlankOriginSource(arena);
                    }
                    backend.load(originPath(arena), originName).whenComplete((world, throwable) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            boolean success = throwable == null && world != null;
                            if (success) {
                                creator.teleportAsync(world.getSpawnLocation());
                                Messages.send(creator, "messages.polar-arena-manager.polar-origin-world-created", world.getName());
                                warmArena(arena);
                            } else if (throwable != null) {
                                plugin.getLogger().severe("[PolarArena] Failed to load origin world for "
                                        + arena.getName() + ": " + throwable.getMessage());
                                throwable.printStackTrace();
                            }
                            callback.accept(success);
                            done.run();
                        });
                    });
                    return;
                }

                creator.teleportAsync(loadedOrigin.getSpawnLocation());
                ok = true;
                warmArena(arena);
            } catch (Throwable throwable) {
                plugin.getLogger().severe(
                        "[PolarArena] Failed to create arena " + arena.getName() + ": " + throwable.getMessage());
                throwable.printStackTrace();
            }
            callback.accept(ok);
            done.run();
        });
    }

    public void loadOriginForEdit(Arena arena, Player player, Consumer<Boolean> callback) {
        if (!isPolarArena(arena)) {
            callback.accept(false);
            return;
        }

        ensureArenaMaps(arena);
        String originName = originWorldName(arena);
        World loaded = Bukkit.getWorld(originName);
        if (loaded != null) {
            player.teleportAsync(loaded.getSpawnLocation());
            callback.accept(true);
            return;
        }

        queue.enqueue("polar-edit-load-" + arena.getName(), done -> backend.load(originPath(arena), originName)
                .whenComplete((world, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean success = throwable == null && world != null;
                    if (success) {
                        player.teleportAsync(world.getSpawnLocation());
                    } else if (throwable != null) {
                        plugin.getLogger()
                                .severe("[PolarArena] Failed to load origin edit world for " + arena.getName());
                        throwable.printStackTrace();
                    }
                    callback.accept(success);
                    done.run();
                })));
    }

    public void saveOrigin(Arena arena, Consumer<Boolean> callback) {
        if (!isPolarArena(arena)) {
            callback.accept(false);
            return;
        }

        queue.enqueue("polar-save-origin-" + arena.getName(), done -> {
            boolean ok = false;
            try {
                World world = Bukkit.getWorld(originWorldName(arena));
                if (world != null) {
                    Files.createDirectories(originPath(arena).getParent());
                    backend.save(world, originPath(arena));

                    refreshOriginCache(arena); // NEW: keep RAM cache synced
                    unusedQueueCopies.removeIf(copy -> key(copy.getArena()).equals(key(arena)));
                    recycleAvailable(arena); // old preloaded instances are now stale
                    warmArena(arena); // create fresh ones from updated cache
                    ok = true;
                }
            } catch (Throwable throwable) {
                plugin.getLogger().severe(
                        "[PolarArena] Failed to save origin for " + arena.getName() + ": " + throwable.getMessage());
                throwable.printStackTrace();
            }
            callback.accept(ok);
            done.run();
        });
    }

    public int availableCopies(Arena arena) {
        if (!isPolarArena(arena)) return 0;
        Deque<PolarArenaReservation> copies = available.get(key(arena));
        return copies == null ? 0 : copies.size();
    }

    public PolarArenaReservation reservePreloadedArena(Arena arena) {
        if (availableCopies(arena) == 0) return null;
        String key = key(arena);
        PolarArenaReservation reservation = available.get(key).removeFirst();
        inUse.get(key).add(reservation);
        unusedQueueCopies.add(reservation);
        warmArena(arena);
        return reservation;
    }

    public void returnUnusedArena(PolarArenaReservation reservation) {
        if (!unusedQueueCopies.remove(reservation)) {
            // The origin may have been republished while this copy was reserved.
            if (plugin.isEnabled()) releaseArena(reservation, true);
            return;
        }
        String key = key(reservation.getArena());
        var used = inUse.get(key);
        if (used != null && used.remove(reservation)) {
            available.get(key).addFirst(reservation);
        }
    }

    public PolarArenaReservation reserveArena(Arena arena) {
        if (!isPolarArena(arena))
            return null;

        ensureArenaMaps(arena);

        // Hard stop, make sure at least one world exists right now
        warmArenaNow(arena);

        Deque<PolarArenaReservation> deque = available.get(key(arena));
        PolarArenaReservation reservation = (deque == null) ? null : deque.pollFirst();

        if (reservation == null) {
            plugin.getLogger().warning("[PolarArena] reserveArena failed for " + arena.getName()
                    + " | origin=" + originPath(arena)
                    + " | available=0");
            return null;
        }

        inUse.computeIfAbsent(key(arena), ignored -> new LinkedHashSet<>()).add(reservation);

        // Refill immediately so the next reserve has one ready too
        warmArenaNow(arena);

        return reservation;
    }

    private void warmArenaNow(Arena arena) {
        if (!isPolarArena(arena))
            return;

        ensureArenaMaps(arena);
        String key = key(arena);

        Deque<PolarArenaReservation> deque = available.get(key);
        Set<PolarArenaReservation> used = inUse.get(key);

        int liveAvailable = deque == null ? 0 : deque.size();
        int liveUsed = used == null ? 0 : used.size();
        int target = Math.max(getPreloadCount(), liveUsed + 1);

        while (liveAvailable < target) {
            int id = nextInstanceIds.compute(key, (ignored, value) -> value == null ? 1 : value + 1);
            String worldName = duelWorldName(arena, id);

            PolarArenaReservation reservation = createRuntimeReservationNow(arena, id, worldName);
            if (reservation == null) {
                plugin.getLogger().warning("[PolarArena] Failed to warm arena " + arena.getName()
                        + " | worldName=" + worldName
                        + " | origin=" + originPath(arena));
                return;
            }

            deque.addLast(reservation);
            liveAvailable++;
        }
    }

    private PolarArenaReservation createRuntimeReservationNow(Arena arena, int id, String worldName) {
        try {
            byte[] originBytes = getCachedOriginBytes(arena);
            World world = backend.create(originBytes, worldName).join();
            if (world == null) {
                plugin.getLogger().severe("[PolarArena] Polar.createWorld returned null for " + worldName);
                return null;
            }

            return newReservation(arena, id, world);
        } catch (Throwable throwable) {
            plugin.getLogger().severe("[PolarArena] Failed to create duel world " + worldName
                    + " for arena " + arena.getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        }
    }

    public void releaseArena(PolarArenaReservation reservation, boolean createReplacement) {
        if (reservation == null)
            return;
        unusedQueueCopies.remove(reservation);
        String key = key(reservation.getArena());
        Set<PolarArenaReservation> used = inUse.get(key);
        if (used != null)
            used.remove(reservation);

        queue.enqueue("polar-release-" + reservation.getWorldName(), done -> {
            try {
                World world = Bukkit.getWorld(reservation.getWorldName());
                if (world != null && !world.getPlayers().isEmpty()) {
                    // Wait 5 seconds and retry (players like spectators are still here)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> releaseArena(reservation, createReplacement), 100L);
                    done.run();
                    return;
                }
                if (world != null) {
                    Bukkit.unloadWorld(world, false);
                }
                deleteWorldFolder(reservation.getWorldName());
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[PolarArena] Failed to unload duel world " + reservation.getWorldName()
                        + ": " + throwable.getMessage());
            }
            if (createReplacement) {
                warmArena(reservation.getArena());
            }
            done.run();
        });
    }

    public void tickMaintenance() {
        if (!isEnabled() || DuelManager.stor == null)
            return;

        List<PolarArenaReservation> missingWorlds = new ArrayList<>();
        for (Set<PolarArenaReservation> reservations : inUse.values()) {
            for (PolarArenaReservation reservation : reservations) {
                if (Bukkit.getWorld(reservation.getWorldName()) == null) {
                    missingWorlds.add(reservation);
                }
            }
        }
        for (PolarArenaReservation reservation : missingWorlds) {
            releaseArena(reservation, true);
        }

        for (Arena arena : DuelManager.stor.arenas) {
            if (!isPolarArena(arena))
                continue;
            warmArena(arena);
        }
    }

    public String originWorldName(Arena arena) {
        return arena.getName().toLowerCase(Locale.ROOT) + "-p-orig";
    }

    private String duelWorldName(Arena arena, int id) {
        return arena.getName().toLowerCase(Locale.ROOT) + "-p-" + id + "-" + UUID.randomUUID().toString().substring(0, 5) + "-duel";
    }

    private void warmArena(Arena arena) {
        if (!isPolarArena(arena))
            return;

        ensureArenaMaps(arena);
        String key = key(arena);

        int availableCount = available.get(key).size();
        int usedCount = inUse.get(key).size();
        int target = Math.max(getPreloadCount(), usedCount + 1);

        if (availableCount >= target)
            return;
        if (!warmQueued.add(key))
            return;

        queue.enqueue("polar-warm-" + arena.getName(), done -> {
            try {
                Deque<PolarArenaReservation> deque = available.get(key);
                Set<PolarArenaReservation> used = inUse.get(key);

                int liveAvailable = deque == null ? 0 : deque.size();
                int liveUsed = used == null ? 0 : used.size();
                int targetNow = Math.max(getPreloadCount(), liveUsed + 1);

                if (liveAvailable >= targetNow) {
                    warmQueued.remove(key);
                    done.run();
                    return;
                }

                if (!Files.exists(originPath(arena))) {
                    warmQueued.remove(key);
                    done.run();
                    return;
                }

                int id = nextInstanceIds.compute(key, (ignored, value) -> value == null ? 1 : value + 1);
                String worldName = duelWorldName(arena, id);

                // UUID-suffixed name is always unique — create from cached origin bytes
                createRuntimeReservationFromCache(arena, id, worldName, key, done);
                return;

            } catch (Throwable throwable) {
                plugin.getLogger().severe(
                        "[PolarArena] Warm task crashed for " + arena.getName() + ": " + throwable.getMessage());
                throwable.printStackTrace();
            }

            warmQueued.remove(key);
            done.run();
        });
    }

    private void recycleAvailable(Arena arena) {
        String key = key(arena);
        Deque<PolarArenaReservation> deque = available.get(key);
        if (deque == null || deque.isEmpty())
            return;
        List<PolarArenaReservation> copy = new ArrayList<>(deque);
        deque.clear();
        for (PolarArenaReservation reservation : copy) {
            releaseArena(reservation, false);
        }
    }

    private void ensureArenaMaps(Arena arena) {
        available.computeIfAbsent(key(arena), ignored -> new ArrayDeque<>());
        inUse.computeIfAbsent(key(arena), ignored -> new LinkedHashSet<>());
        nextInstanceIds.putIfAbsent(key(arena), 0);
    }

    private PolarArenaReservation newReservation(Arena arena, int id, World world) {
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
        Location mother = new Location(world, 0.0D, 0.0D, 0.0D);
        return new PolarArenaReservation(arena, id, world.getName(), mother);
    }

    private String key(Arena arena) {
        return arena.getName().toLowerCase(Locale.ROOT);
    }

    private Path originPath(Arena arena) {
        org.bukkit.plugin.Plugin polarPlugin = plugin.getServer()
                .getPluginManager()
                .getPlugin("polarpaper");

        if (polarPlugin == null) {
            throw new IllegalStateException("PolarPaper plugin is not loaded");
        }

        return polarPlugin.getDataFolder().toPath()
                .resolve("worlds")
                .resolve(originWorldName(arena) + ".polar");
    }

    private byte[] getCachedOriginBytes(Arena arena) throws IOException {
        String key = key(arena);

        byte[] cached = cachedOriginBytes.get(key);
        if (cached != null && cached.length > 0) {
            return Arrays.copyOf(cached, cached.length);
        }

        Path path = originPath(arena);
        if (!Files.exists(path)) {
            throw new IOException("Missing polar origin file: " + path);
        }

        byte[] loaded = Files.readAllBytes(path);
        cachedOriginBytes.put(key, loaded);
        return Arrays.copyOf(loaded, loaded.length);
    }

    private void refreshOriginCache(Arena arena) throws IOException {
        Path path = originPath(arena);
        if (!Files.exists(path)) {
            cachedOriginBytes.remove(key(arena));
            return;
        }

        cachedOriginBytes.put(key(arena), Files.readAllBytes(path));
    }

    private void invalidateOriginCache(Arena arena) {
        cachedOriginBytes.remove(key(arena));
    }

    private void createRuntimeReservationFromCache(Arena arena, int id, String worldName, String key, Runnable done) {
        final byte[] originBytes;
        try {
            originBytes = getCachedOriginBytes(arena);
        } catch (Throwable throwable) {
            plugin.getLogger().severe(
                    "[PolarArena] Failed to read cached origin for " + arena.getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
            warmQueued.remove(key);
            done.run();
            Bukkit.getScheduler().runTask(plugin, () -> warmArena(arena));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                backend.create(originBytes, worldName)
                        .whenComplete((world, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                if (throwable != null) {
                                    plugin.getLogger().severe("[PolarArena] Failed to create cached duel world for "
                                            + arena.getName() + ": " + throwable.getMessage());
                                    throwable.printStackTrace();
                                    return;
                                }

                                if (world != null) {
                                    available.get(key).addLast(newReservation(arena, id, world));
                                } else {
                                    plugin.getLogger()
                                            .severe("[PolarArena] Polar.createWorld returned null for " + worldName);
                                }
                            } finally {
                                warmQueued.remove(key);
                                done.run();
                                Bukkit.getScheduler().runTask(plugin, () -> warmArena(arena));
                            }
                        }));
            } catch (Throwable throwable) {
                plugin.getLogger().severe("[PolarArena] Failed to create cached duel world for " + arena.getName()
                        + ": " + throwable.getMessage());
                throwable.printStackTrace();
                warmQueued.remove(key);
                done.run();
                Bukkit.getScheduler().runTask(plugin, () -> warmArena(arena));
            }
        });
    }

    private void createBlankOriginSource(Arena arena) throws IOException {
        String tempWorldName = arena.getName() + "-p-bootstrap-" + System.nanoTime();
        World tempWorld = Bukkit.createWorld(new WorldCreator(tempWorldName)
                .type(WorldType.FLAT)
                .generateStructures(false));
        if (tempWorld == null) {
            throw new IOException("Could not create temporary bootstrap world for " + arena.getName());
        }

        try {
            Files.write(originPath(arena), backend.encode(tempWorld));
        } finally {
            Bukkit.unloadWorld(tempWorld, false);
            deleteWorldFolder(tempWorldName);
        }
    }

    private void deleteWorldFolder(String worldName) {
        try {
            Path folder = Bukkit.getWorldContainer().toPath().resolve(worldName);
            if (!Files.exists(folder))
                return;
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Throwable throwable) {
            plugin.getLogger().warning(
                    "[PolarArena] Failed to delete world folder for " + worldName + ": " + throwable.getMessage());
        }
    }
}
