package org.albi.core.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.albi.core.data.StatManagement.RedisWrapper;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.XAddParams;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/** Shared data with a useful Redis-free equivalent. All calls that access the backend run off-thread. */
public final class SharedState {
    private static final int KIT_TTL = 30 * 24 * 60 * 60;
    private static final String KIT_PREFIX = "sharedkits:getkit:";
    private static final Gson JSON = new Gson();
    private static final TypeToken<Map<String, String>> HEALTH_TYPE = new TypeToken<>() {};

    // Keep the existing keys and commit both writes together, so the issued code is always redeemable.
    private static final String SHARE_KIT = """
            local code = redis.call('GET', KEYS[1])
            if not code then
                code = ARGV[2]
                if redis.call('EXISTS', ARGV[1] .. code) == 1 then return false end
            end
            redis.call('SETEX', ARGV[1] .. code, ARGV[3], ARGV[4])
            redis.call('SETEX', KEYS[1], ARGV[3], code)
            return code
            """;
    private static final String RELAY_HEALTH = """
            local previous = tonumber(redis.call('HGET', KEYS[1], 'timestamp_ms') or '0')
            if previous >= tonumber(ARGV[1]) then return 1 end
            redis.call('HSET', KEYS[1], unpack(ARGV, 2))
            return 1
            """;

    private final RedisWrapper redis;
    private final DataSource database;
    private final boolean useExperimental;
    private final SqlRedisRelay relay;
    private final BooleanSupplier sqlReady;
    private volatile SqlSharedKeys sql;
    private final Logger logger;

    SharedState(RedisWrapper redis, DataSource database, boolean experimental, boolean failover, SqlRedisRelay relay,
                BooleanSupplier sqlReady, Logger logger) {
        this.redis = redis;
        this.database = database;
        this.relay = relay;
        this.sqlReady = sqlReady;
        this.logger = logger;
        if (redis.isEnabled() && experimental && !failover) {
            logger.warning("[Storage] Redis is enabled. Experimental MySQL shared state requires redis.fallback_to_mysql: true to act as a fallback.");
        }
        if (redis.isEnabled() && failover && !experimental) {
            logger.warning("[Storage] Redis fallback uses local gameplay. Set storage.experimental_sql_shared_state: true to opt in to MySQL shared state.");
        }
        useExperimental = experimental && (!redis.isEnabled() || failover);
        if (useExperimental) {
            logger.warning("[Storage] EXPERIMENTAL MySQL shared state is enabled. It is not recommended. Use only if you do not have Redis.");
        }
    }

    void prepareSql() throws SQLException {
        if (useExperimental && sql == null) sql = new SqlSharedKeys(database);
    }

    public boolean isEnabled() { return redis.isEnabled() || useExperimental; }

    private boolean canUseSql() { return sql != null && sqlReady.getAsBoolean(); }

    public String shareKit(UUID owner, int slot, String json) {
        String ownerKey = "sharedkits:getcode:" + owner + ":" + slot;
        String shared = redis.withJedis(j -> {
            for (int attempt = 0; attempt < 8; attempt++) {
                Object result = j.eval(SHARE_KIT, List.of(ownerKey), List.of(KIT_PREFIX, newCode(), Integer.toString(KIT_TTL), json));
                if (result instanceof String code) return code;
            }
            return null;
        });
        if (shared != null || redis.isHealthy() || !canUseSql()) return shared;
        // Distinct codes keep SQL and Redis shares addressable after either service recovers.
        return runSql("share kit", () -> sql.shareKit(ownerKey, json, KIT_TTL, () -> "S" + newCode()));
    }

    public String readKit(String code) {
        if (code.matches("S[0-9A-F]{8}") && canUseSql()) return runSql("look up kit", () -> sql.get(kitKey(code)));
        if (code.matches("[0-9A-F]{8}")) return redis.withJedis(j -> j.get(kitKey(code)));
        return null;
    }

    public boolean isKitBackendAvailable(String code) {
        if (code.matches("S[0-9A-F]{8}")) return canUseSql();
        if (code.matches("[0-9A-F]{8}")) return redis.isHealthy();
        return isEnabled();
    }

    static String kitKey(String code) { return KIT_PREFIX + code; }

    private static String newCode() { return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT); }

    public void recordHealth(String server, Map<String, String> data, int maxSamples, int ttl) {
        String key = healthKey(server);
        Boolean published = redis.withJedis(j -> {
            try (var pipeline = j.pipelined()) {
                pipeline.hmset(key, data);
                pipeline.xadd("server:" + server + ":health:stream",
                        XAddParams.xAddParams().maxLen(maxSamples).approximateTrimming(), data);
                pipeline.sync();
            }
            return true;
        });
        if (published == null && !redis.isHealthy() && canUseSql()) {
            runSql("write heartbeat", () -> {
                sql.put(key, JSON.toJson(data), ttl);
                return null;
            });
        }
        if (published == null && redis.isEnabled() && !redis.isHealthy() && relay.available() && sqlReady.getAsBoolean()) {
            runSql("ask a Redis peer to publish the heartbeat", () -> {
                relay.offer("health:" + server, JSON.toJson(data), Math.min(ttl, 30));
                return null;
            });
        }
    }

    Boolean publishRelayedHealth(String server, String payload) {
        Map<String, String> data = decodeHealth(payload);
        if (data == null || !server.equals(data.get("server")) || data.size() > 512) return true;
        String time = data.get("timestamp_ms");
        if (time == null || !time.matches("[0-9]{1,17}")) return true;
        var arguments = new ArrayList<String>();
        arguments.add(time);
        data.forEach((key, value) -> {
            if (key != null && value != null) {
                arguments.add(key);
                arguments.add(value);
            }
        });
        return redis.withJedis(j -> Long.valueOf(1L).equals(j.eval(RELAY_HEALTH, List.of(healthKey(server)), arguments)));
    }

    public Map<String, String> readHealth(String server) {
        String key = healthKey(server);
        var data = redis.withJedis(j -> j.hgetAll(key));
        if (data != null || redis.isHealthy() || !canUseSql()) return data;
        return decodeHealth(runSql("read heartbeat", () -> sql.get(key)));
    }

    public List<Map<String, String>> healthSnapshots() {
        var snapshots = redis.withJedis(j -> {
            List<Map<String, String>> list = new ArrayList<>();
            var seen = new HashSet<String>();
            ScanParams scan = new ScanParams().match("server:*:health:latest");
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                var page = j.scan(cursor, scan);
                for (String key : page.getResult()) if (seen.add(key)) list.add(j.hgetAll(key));
                cursor = page.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            return list;
        });
        if (snapshots != null) return snapshots;
        if (redis.isHealthy() || !canUseSql()) return List.of();
        var stored = runSql("discover servers", sql::healthSnapshots);
        return stored == null ? List.of() : stored.stream().map(SharedState::decodeHealth).filter(Objects::nonNull).toList();
    }

    private static String healthKey(String server) { return "server:" + server + ":health:latest"; }

    private static Map<String, String> decodeHealth(String payload) {
        try {
            return payload == null ? null : JSON.fromJson(payload, HEALTH_TYPE);
        } catch (JsonParseException invalid) {
            return null;
        }
    }

    void purgeExpired() {
        if (sql != null) runSql("purge expired keys", () -> {
            sql.purgeExpired();
            return null;
        });
    }

    private <T> T runSql(String operation, SqlOperation<T> action) {
        try {
            return action.run();
        } catch (SQLException failure) {
            logFailure(operation, failure);
            return null;
        }
    }

    private void logFailure(String operation, SQLException failure) {
        logger.warning("[Storage] Experimental MySQL shared state could not " + operation + " (" + failure.getSQLState() + ").");
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }
}
