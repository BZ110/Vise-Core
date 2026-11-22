package org.albi.core.modules.Duels;


import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Queue And Caching Schematic Management System.
 * Use a global version of this in a Core plugin.
 *
 * @author Critical <3
 * @version 1.0
 */
public class SchematicManager {
    private static final long RETRY_DELAY = TimeUnit.SECONDS.toNanos(2);

    private final JavaPlugin plugin; // Reference to your main plugin class
    private final boolean fastAsyncWorldEdit;
    private final Queue<PasteTask> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isPasting = new AtomicBoolean(false);

    private record CachedClipboard(FileTime modified, long size, Clipboard clipboard) {}

    // FAWE reads on a worker. Key by path because different arenas can use the same filename.
    private final ConcurrentHashMap<Path, CachedClipboard> cacheSchem = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, Long> failedPastes = new ConcurrentHashMap<>();

    // Basic constructor, pass in your JavaPlugin class. (The variable "this" inside your main class)
    public SchematicManager(JavaPlugin plugin) {
        this.plugin = plugin;
        fastAsyncWorldEdit = plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        plugin.getLogger().info("[SchematicManager] Loaded.");
    }

    /**
     * Enqueue a schematic paste task.
     * Schematic files are put in a cache once pasted.
     *
     * @param location where to paste the schematic
     * @param file     schematic file
     * @param air      whether to paste air blocks (true = include air blocks)
     */
    public void enqueueSchematic(Location location, File file, boolean air) {
        enqueueTrackedSchematic(location, file, air);
    }

    public CompletableFuture<Boolean> enqueueTrackedSchematic(Location location, File file, boolean air) {
        plugin.getLogger().warning("the schematic: '" + file.getName() + "' has been queued.");
        var completion = new CompletableFuture<Boolean>();
        queue.offer(new PasteTask(location, file, air, completion));
        processNext();
        return completion;
    }

    boolean canReserve(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        Long retryAt = failedPastes.get(path);
        if (retryAt == null) return true;
        if (retryAt > System.nanoTime()) return false;
        return failedPastes.remove(path, retryAt);
    }

    /**
     * Process the next schematic in the queue, if any.
     */
    private void processNext() {
        if (isPasting.get() || queue.isEmpty()) {
            return;
        }
        if (!isPasting.compareAndSet(false, true)) {  // Atomic check to ensure only one paste is active.
            return;
        }
        final PasteTask task = queue.poll();
        if (task == null) {
            isPasting.set(false);
            return;
        }
        Runnable paste = () -> {
            boolean success = false;
            try {
                success = pasteHelper(task.location, task.file, task.air);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Schematic paste failed", e);
            }
            if (!success) {
                // Let matchmaking try another arena while this file is being repaired.
                failedPastes.put(task.file.toPath().toAbsolutePath().normalize(), System.nanoTime() + RETRY_DELAY);
                plugin.getLogger().warning("Failed to paste schematic: " + task.file.getName());
            }
            // Optionally add a short delay before processing the next task.
            boolean pasted = success;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                task.completion.complete(pasted);
                isPasting.set(false);
                plugin.getLogger().info("Finished pasting queued schematic.");
                processNext();
            }, 5L);
        };
        // The Bukkit WorldEdit adapter accesses live blocks on the tick thread.
        if (fastAsyncWorldEdit) Bukkit.getScheduler().runTaskAsynchronously(plugin, paste);
        else Bukkit.getScheduler().runTask(plugin, paste);
    }

    /**
     * Paste a schematic at the given location.
     * Returns true if the paste operation was successful, false otherwise.
     *
     * @param location where to paste the schematic
     * @param file     schematic file
     * @param air      whether to paste air blocks (true = include air blocks)
     * @return success state as a boolean.
     */
    public boolean pasteHelper(Location location, File file, boolean air) {
        // Compute the paste position as a BlockVector3.
        BlockVector3 pastePos = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        try {
            if (location.getWorld() == null) {
                plugin.getLogger().warning("World is null for location: " + location);
                return false;
            }

            Clipboard clipboard = loadClipboard(file);

            World weWorld = BukkitAdapter.adapt(location.getWorld());
            var builder = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld);
            if (fastAsyncWorldEdit) builder.fastMode(true);
            try (EditSession editSession = builder.build()) {
                // Build and complete the paste operation.
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operation op = holder.createPaste(editSession)
                        .to(pastePos)
                        .ignoreAirBlocks(!air)
                        .build();
                Operations.complete(op);
                if (fastAsyncWorldEdit) editSession.flushQueue();
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read schematic " + file.getName() + ". " + e.getMessage());
            return false;
        } catch (WorldEditException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not paste " + file.getName(), e);
            return false;
        }
    }

    private Clipboard loadClipboard(File file) throws IOException {
        Path path = file.toPath().toRealPath();
        var attrs = Files.readAttributes(path, BasicFileAttributes.class);
        CachedClipboard cached = cacheSchem.get(path);
        if (cached != null && cached.modified.equals(attrs.lastModifiedTime()) && cached.size == attrs.size()) {
            return cached.clipboard;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("Unsupported schematic format");
        try (var input = new FileInputStream(file); var reader = format.getReader(input)) {
            Clipboard clipboard = reader.read();
            cacheSchem.put(path, new CachedClipboard(attrs.lastModifiedTime(), attrs.size(), clipboard));
            return clipboard;
        }
    }

    /**
     * Internal class to hold schematic paste tasks.
     */
    private static class PasteTask {
        final Location location;
        final File file;
        final boolean air;
        final CompletableFuture<Boolean> completion;

        PasteTask(Location location, File file, boolean air, CompletableFuture<Boolean> completion) {
            this.location = location;
            this.file = file;
            this.air = air;
            this.completion = completion;
        }
    }
}
