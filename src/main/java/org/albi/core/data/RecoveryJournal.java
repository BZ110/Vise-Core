package org.albi.core.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** Local snapshots and a durable outbox. One file per write keeps acknowledgements independent. */
final class RecoveryJournal {
    record Profile(String json) {} // null JSON represents a known absent row.
    record Write(UUID id, long created, String type, String subject, Profile base, String json, String name) {
        String key() { return type + ":" + subject; }
    }

    static final Gson JSON = new Gson();
    private final Path snapshots;
    private final Path queue;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Write> pending = new ConcurrentHashMap<>();
    private final AtomicLong clock = new AtomicLong();

    RecoveryJournal(Path root, Logger logger) {
        snapshots = root.resolve("profiles");
        queue = root.resolve("outbox");
        this.logger = logger;
        if (Files.isDirectory(queue)) {
            try (var files = Files.list(queue)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                    Write write = read(path, Write.class);
                    if (write != null && valid(write)) {
                        pending.put(write.id(), write);
                        clock.accumulateAndGet(write.created(), Math::max);
                    }
                });
            } catch (IOException failure) {
                logger.severe("[Recovery] Could not read the local outbox. Check permissions on " + queue);
            }
        }
    }

    Write newWrite(String type, String subject, Profile base, String json, String name) {
        long created = clock.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()));
        return new Write(UUID.randomUUID(), created, type, subject, base, json, name);
    }

    boolean enqueue(Write write) {
        if (!store(queue.resolve(write.id() + ".json"), write)) return false;
        pending.put(write.id(), write);
        return true;
    }

    void acknowledge(Write write) {
        try {
            Files.deleteIfExists(queue.resolve(write.id() + ".json"));
            pending.remove(write.id());
        } catch (IOException failure) {
            logger.warning("[Recovery] Could not remove an acknowledged outbox file. It will be safe to retry.");
        }
    }

    Write latest(String key) {
        return pending.values().stream().filter(write -> write.key().equals(key))
                .max(Comparator.comparingLong(Write::created)).orElse(null);
    }

    List<Write> pending() {
        return pending.values().stream().sorted(Comparator.comparingLong(Write::created)).toList();
    }

    Profile readProfile(String type, UUID id) {
        return read(snapshots.resolve(type + "-" + id + ".json"), Profile.class);
    }

    void rememberProfile(String type, UUID id, Profile profile) {
        store(snapshots.resolve(type + "-" + id + ".json"), profile);
    }

    private <T> T read(Path path, Class<T> type) {
        if (!Files.isRegularFile(path)) return null;
        try {
            return JSON.fromJson(Files.readString(path), type);
        } catch (IOException | JsonParseException failure) {
            logger.warning("[Recovery] Could not read " + path.getFileName() + ". The file was retained.");
            return null;
        }
    }

    private boolean store(Path target, Object value) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".recovery-", ".tmp");
            try {
                try (var file = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    var data = ByteBuffer.wrap(JSON.toJson(value).getBytes(StandardCharsets.UTF_8));
                    while (data.hasRemaining()) file.write(data);
                    file.force(true);
                }
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (IOException failure) {
            logger.severe("[Recovery] Could not persist " + target.getFileName() + " to disk. Check free space and permissions.");
            return false;
        }
    }

    static boolean valid(Write write) {
        if (write.id() == null || write.json() == null || write.subject() == null || write.type() == null) return false;
        return switch (write.type()) {
            case "main", "stats" -> write.subject().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
            case "match" -> write.subject().matches("[0-9A-Za-z]{1,8}");
            default -> false;
        };
    }
}
