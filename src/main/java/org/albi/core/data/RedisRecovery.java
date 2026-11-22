package org.albi.core.data;

import com.google.gson.JsonParseException;
import org.albi.core.data.RecoveryJournal.Write;
import org.albi.core.data.StatManagement.RedisWrapper;
import redis.clients.jedis.params.ScanParams;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable Redis requests and acknowledgements shared by Core instances using the same services. */
final class RedisRecovery {
    private static final String OFFER = """
            if redis.call('EXISTS', KEYS[3]) == 1 then return 2 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
            local raw = redis.call('HGET', KEYS[2], ARGV[2])
            if not raw or cjson.decode(raw).created < tonumber(ARGV[4]) then
                redis.call('HSET', KEYS[2], ARGV[2], ARGV[3])
            end
            return 1
            """;
    private static final String ACK = """
            redis.call('SETEX', KEYS[3], 604800, '1')
            redis.call('HDEL', KEYS[1], ARGV[1])
            local raw = redis.call('HGET', KEYS[2], ARGV[2])
            if raw and cjson.decode(raw).id == ARGV[1] then redis.call('HDEL', KEYS[2], ARGV[2]) end
            return 1
            """;

    private final RedisWrapper redis;
    private final String prefix;
    private final String outbox;
    private final String latest;
    private String cursor = ScanParams.SCAN_POINTER_START;

    RedisRecovery(RedisWrapper redis, String namespace) {
        this.redis = redis;
        var id = UUID.nameUUIDFromBytes(namespace.getBytes(StandardCharsets.UTF_8));
        prefix = "vise:recovery:{" + id + "}:";
        outbox = prefix + "outbox";
        latest = prefix + "latest";
    }

    boolean ask(Write write) {
        if (!redis.isEnabled()) return false;
        var result = redis.withJedis(j -> j.eval(OFFER, keys(write), List.of(write.id().toString(), write.key(),
                RecoveryJournal.JSON.toJson(write), Long.toString(write.created()))));
        return result instanceof Long n && n > 0;
    }

    boolean acknowledged(Write write) {
        return Boolean.TRUE.equals(redis.withJedis(j -> j.exists(receipt(write))));
    }

    void acknowledge(Write write) {
        redis.withJedis(j -> j.eval(ACK, keys(write), List.of(write.id().toString(), write.key())));
    }

    Write latest(String key) {
        return decode(redis.withJedis(j -> j.hget(latest, key)));
    }

    List<Write> pending() {
        var page = redis.withJedis(j -> j.hscan(outbox, cursor, new ScanParams().count(32)));
        if (page == null) return List.of();
        cursor = page.getCursor();
        return page.getResult().stream().map(Map.Entry::getValue).map(RedisRecovery::decode)
                .filter(Objects::nonNull).sorted(Comparator.comparingLong(Write::created)).toList();
    }

    private static Write decode(String payload) {
        if (payload == null) return null;
        try {
            var write = RecoveryJournal.JSON.fromJson(payload, Write.class);
            return write != null && RecoveryJournal.valid(write) ? write : null;
        } catch (JsonParseException invalid) {
            return null;
        }
    }

    private List<String> keys(Write write) { return List.of(outbox, latest, receipt(write)); }

    private String receipt(Write write) { return prefix + "ack:" + write.id(); }
}
