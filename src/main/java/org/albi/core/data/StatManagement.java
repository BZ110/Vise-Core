/*─────────────────────────────────────────────────────────────────────────────
 *  org.albi.core.data.StatManagement   (fixed Redis handling)
 *───────────────────────────────────────────────────────────────────────────*/
package org.albi.core.data;

import com.google.gson.Gson;
import com.google.common.cache.CacheBuilder;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.albi.core.modules.Duels.ranked.RankedMatchRecord;
import org.albi.core.util.ConfigValues;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.*;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.lang.reflect.Type;
import java.security.cert.CertificateFactory;
import java.sql.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.function.Function;

public final class StatManagement {

    /*─────────────────────────────────────────────────────────────────────────*/
    /*  PUBLIC FACADE (unchanged)                                             */
    /*─────────────────────────────────────────────────────────────────────────*/
    public static void init(Plugin plugin, Config cfg) {
        int retentionDays = Math.max(1, plugin.getConfig().getInt("ranked.history_expiry_days", 7));
        StatStore store = new StatStore(plugin, cfg, retentionDays);
        INTERNAL.store = store;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, store::recover, 20L, 100L);
        if (!cfg.redisEnabled()) {
            plugin.getLogger().info("[Storage] Redis is disabled. Network queues and chat are unavailable.");
        }
    }

    public static void shutdown() {
        StatStore store = INTERNAL.store;
        if (store == null) return;
        try {
            if (!store.writes.finish(() -> flush(store, true), store.shutdownTimeout)) {
                store.plugin.getLogger().warning("[Storage] Final flush exceeded the shutdown timeout. Some writes may not have reached durable storage.");
            }
        } finally {
            INTERNAL.store = null;
            store.getRedis().close();
            store.getSql().close();
            INTERNAL.cache.clear();
            INTERNAL.mainCache.clear();
            synchronized (INTERNAL.sessions) {
                INTERNAL.pendingQuit.clear();
            }
        }
    }

    public static void attach(UUID id, String name) {
        StatStore store = INTERNAL.store;
        if (store == null) return;
        store.names.put(id, name);
        synchronized (INTERNAL.sessions) {
            INTERNAL.pendingQuit.remove(id);
        }
    }

    public static CompletableFuture<PlayerStat> getAsync(UUID id, String name) {
        StatStore store = INTERNAL.store;
        if (name != null && !name.isBlank() && !name.equals("<unknown>")) store.names.asMap().putIfAbsent(id, name);
        return CompletableFuture.supplyAsync(() -> INTERNAL.cache.computeIfAbsent(id, store::load));
    }

    public static void saveAndRemove(UUID id) {
        StatStore store = INTERNAL.store;
        if (store == null) return;
        var quit = new Object();
        synchronized (INTERNAL.sessions) {
            INTERNAL.pendingQuit.put(id, quit);
        }
        store.writes.submit(() -> saveDeparture(store, id, quit));
    }

    private static void saveDeparture(StatStore store, UUID id, Object quit) {
        synchronized (INTERNAL.sessions) {
            if (INTERNAL.pendingQuit.get(id) != quit) return;
        }
        var stats = INTERNAL.cache.get(id);
        var profile = INTERNAL.mainCache.get(id);
        if (!persist(store, stats, profile, true)) return;
        synchronized (INTERNAL.sessions) {
            if ((stats != null && stats.isDirty()) || (profile != null && profile.isDirty())) return;
            if (!INTERNAL.pendingQuit.remove(id, quit)) return;
            if (stats != null) INTERNAL.cache.remove(id, stats);
            if (profile != null) INTERNAL.mainCache.remove(id, profile);
        }
    }

    public static void flushAllAsync() {
        StatStore store = INTERNAL.store;
        if (store == null || !store.flushing.compareAndSet(false, true)) return;
        if (!store.writes.submit(() -> {
            try {
                flush(store, false);
            } finally {
                store.flushing.set(false);
            }
        })) store.flushing.set(false);
    }

    private static void flush(StatStore store, boolean shutdown) {
        Map<UUID, Object> quit;
        synchronized (INTERNAL.sessions) {
            quit = shutdown ? Map.of() : Map.copyOf(INTERNAL.pendingQuit);
        }
        var ids = new HashSet<>(INTERNAL.cache.keySet());
        ids.addAll(INTERNAL.mainCache.keySet());
        ids.addAll(quit.keySet());
        int failed = 0;
        for (UUID id : ids) {
            if (Thread.currentThread().isInterrupted()) return;
            Object departure = quit.get(id);
            if (departure != null) {
                saveDeparture(store, id, departure);
            } else if (!persist(store, INTERNAL.cache.get(id), INTERNAL.mainCache.get(id), !shutdown)) {
                failed++;
            }
        }
        if (shutdown && failed > 0) {
            store.plugin.getLogger().severe("[Storage] Could not save or queue " + failed + " profiles at shutdown.");
        }
    }

    private static boolean persist(StatStore store, PlayerStat stats, MainPlayer profile, boolean updateRedis) {
        try {
            boolean statsSaved = stats == null || store.save(stats, updateRedis);
            boolean profileSaved = profile == null || store.saveMain(profile, updateRedis);
            return statsSaved && profileSaved;
        } catch (RuntimeException failure) {
            store.plugin.getLogger().log(Level.WARNING, "[Stats] Could not save player data.", failure);
            return false;
        }
    }

    /** Flushes the source snapshot before a destination server starts loading it. Call off-thread. */
    public static boolean flushForTransfer(PlayerStat stat, MainPlayer profile) {
        StatStore store = INTERNAL.store;
        if (store == null || !store.sqlReady || !persist(store, stat, profile, true) || !store.sqlReady) return false;
        UUID player = profile != null ? profile.uuid() : stat == null ? null : stat.uuid();
        return player != null && store.journal.latest("main:" + player) == null && store.journal.latest("stats:" + player) == null;
    }

    public static boolean isSqlReady() {
        var store = INTERNAL.store;
        return store != null && store.sqlReady;
    }

    public static String getName(UUID id) {
        var store = INTERNAL.store;
        String name = store.names.getIfPresent(id);
        return name != null ? name : store.redis.withJedis(j -> j.hget(Leaderboards.NAMES, id.toString()));
    }

    public static void resetWinStreak(UUID id, String game) {
        getAsync(id, "<unknown>").thenAccept(a -> a.resetWinStreak(game));
    }

    public static List<LBRow> leaderboard(String game, Field field, int count) {
        return INTERNAL.store.leaderboards.top(Leaderboards.Board.game(game, field), count);
    }

    public static List<LBRow> rankedLeaderboard(int count) {
        return INTERNAL.store.leaderboards.top(Leaderboards.Board.overall(), count);
    }

    public static HikariDataSource getSql() {
        return INTERNAL.store.getSql();
    }

    public static RedisWrapper getRedis() {
        return INTERNAL.store.getRedis();
    }

    public static SharedState getSharedState() {
        return INTERNAL.store.shared;
    }

    /*────────────────── MAIN‑PROFILE FACADE ──────────────────*/
    public static MainPlayer getMain(UUID id) {
        return INTERNAL.mainCache.computeIfAbsent(id, INTERNAL.store::loadMain);
    }

    public static CompletableFuture<MainPlayer> getMainAsync(org.bukkit.entity.Player p) {
        StatStore store = INTERNAL.store;
        UUID id = p.getUniqueId();
        return CompletableFuture.supplyAsync(() -> INTERNAL.mainCache.computeIfAbsent(id, store::loadMain));
    }

    public static MainPlayer getMain(org.bukkit.entity.Player p) {
        return getMain(p.getUniqueId());
    }

    public static void saveMatchRecord(RankedMatchRecord record) {
        StatStore store = INTERNAL.store;
        if (store != null) store.writes.submit(() -> store.saveMatch(record));
    }

    public static RankedMatchRecord loadMatchRecord(String matchId) {
        return INTERNAL.store.loadMatch(matchId);
    }

    public static List<RankedMatchRecord> loadRecentMatches(UUID playerId, int limit) {
        return INTERNAL.store.loadRecent(playerId, limit);
    }

    public static UUID findRecentMatchPlayer(String name) {
        return INTERNAL.store.findRecentMatchPlayer(name.toLowerCase(Locale.ROOT));
    }


    /*────────────────── internal glue ──────────────────*/
    private static final class INTERNAL {
        private static volatile StatStore store;

        private static final Map<UUID, PlayerStat> cache = new ConcurrentHashMap<>();
        private static final Map<UUID, MainPlayer> mainCache = new ConcurrentHashMap<>();
        private static final Object sessions = new Object();
        private static final Map<UUID, Object> pendingQuit = new HashMap<>();
    }


    /*────────────────── simple records/enums ───────────*/
    public record Config(
            String mysqlHost, int mysqlPort, String mysqlDatabase, String mysqlUser, String mysqlPass,
            String mysqlSslMode, int mysqlPoolSize, boolean redisEnabled, String redisHost, int redisPort,
            String redisPass, boolean sqlSharedState, boolean redisFallbackToMysql) {

        public static Config from(ConfigurationSection yaml) {
            String sslMode = yaml.getString("mysql.ssl_mode", "").strip().toUpperCase(Locale.ROOT);
            if (sslMode.isEmpty()) sslMode = yaml.getBoolean("mysql.ssl") ? "REQUIRED" : "DISABLED";
            if (!Set.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY").contains(sslMode)) {
                throw new IllegalArgumentException("mysql.ssl_mode must be a Connector/J TLS mode.");
            }
            // Older files relied on Redis being enabled. A newly generated file has an explicit opt-out.
            boolean redisEnabled = !yaml.contains("redis.enabled", true) || yaml.getBoolean("redis.enabled");
            String redisPassword = redisEnabled ? ConfigValues.envOrYaml(yaml, "redis.password", "VISE_REDIS_PASSWORD") : "";
            int poolSize = yaml.getInt("mysql.pool_size", 20);
            if (poolSize < 1) throw new IllegalArgumentException("mysql.pool_size must be positive.");

            return new Config(
                    required(yaml, "mysql.host", "VISE_MYSQL_HOST"),
                    port(yaml, "mysql.port", "VISE_MYSQL_PORT", 3306),
                    required(yaml, "mysql.database", "VISE_MYSQL_DATABASE"),
                    required(yaml, "mysql.user", "VISE_MYSQL_USER"),
                    ConfigValues.envOrYaml(yaml, "mysql.password", "VISE_MYSQL_PASSWORD"),
                    sslMode, poolSize, redisEnabled,
                    redisEnabled ? required(yaml, "redis.host", "VISE_REDIS_HOST") : "",
                    redisEnabled ? port(yaml, "redis.port", "VISE_REDIS_PORT", 6379) : 0,
                    redisPassword.isEmpty() ? null : redisPassword,
                    yaml.getBoolean("storage.experimental_sql_shared_state", false),
                    yaml.getBoolean("redis.fallback_to_mysql", false));
        }

        private static String required(ConfigurationSection yaml, String path, String env) {
            String value = ConfigValues.envOrYaml(yaml, path, env).strip();
            if (value.isEmpty()) throw new IllegalArgumentException("Set " + path + " or " + env + ".");
            return value;
        }

        private static int port(ConfigurationSection yaml, String path, String env, int fallback) {
            String override = System.getenv(env);
            try {
                int port = override == null
                        ? Integer.parseInt(yaml.getString(path, Integer.toString(fallback)))
                        : Integer.parseInt(override.strip());
                if (port >= 1 && port <= 65535) return port;
            } catch (NumberFormatException ignored) {
                // The error below reports the setting name without the entered value.
            }
            throw new IllegalArgumentException(path + " or " + env + " must be a port from 1 to 65535.");
        }
    }

    public enum Field {
        KILLS("kills", "kills"),
        DEATHS("deaths", "deaths"),
        WINS("wins", "wins"),
        STREAK("streak", "winStreak"),
        ROUNDS("rounds", "roundsPlayed"),
        TIME("time", "timePlayed");

        public final String redis;
        final String json;

        Field(String redis, String json) {
            this.redis = redis;
            this.json = json;
        }
    }

    public record LBRow(UUID uuid, long score, String name) {
        public LBRow(UUID uuid, long score) {
            this(uuid, score, null);
        }
    }

    /*────────────────── stat beans (unchanged) ─────────*/
    public static final class Stat {
        private static final Gson G = new Gson();

        /* ── numeric fields ── */
        private int kills, deaths, wins, winStreak, roundsPlayed, timePlayed;

        /* ── increment helpers ── */
        public void addKills(int n)       { kills        += n; }
        public void addDeaths(int n)      { deaths       += n; }
        public void addWin() {            /* one win = +1 wins, +1 winStreak */
            wins++; winStreak++;
        }
        public void resetWinStreak()      { winStreak     = 0; }
        public void addRound()            { roundsPlayed += 1; }
        public void addTime(int seconds)  { timePlayed   += seconds; }

        /* ── getters (use in leaderboards or API) ── */
        public int kills()        { return kills; }
        public int deaths()       { return deaths; }
        public int wins()         { return wins; }
        public int winStreak()    { return winStreak; }
        public int roundsPlayed() { return roundsPlayed; }
        public int timePlayed()   { return timePlayed; }
    }

    public static final class PlayerStat {
        private final UUID uuid;
        private final Map<String, Stat> games = new HashMap<>();
        private final Set<String> dirtyGames = new HashSet<>();
        private final Object writeLock = new Object();
        private long revision;
        private long persistedRevision;

        public PlayerStat(UUID id) { uuid = id; }

        public synchronized Stat game(String name) {
            revision++;
            dirtyGames.add(name);
            return games.computeIfAbsent(name, key -> new Stat());
        }

        public synchronized void addKill(String game) { game(game).addKills(1); }
        public synchronized void addDeath(String game) { game(game).addDeaths(1); }
        public synchronized void addWin(String game) { game(game).addWin(); }
        public synchronized void addRound(String game) { game(game).addRound(); }
        public synchronized void addTime(String game, int seconds) { game(game).addTime(seconds); }
        public synchronized void resetWinStreak(String game) { game(game).resetWinStreak(); }

        public UUID uuid() { return uuid; }
        public synchronized Map<String, Stat> all() { return Map.copyOf(games); }
        public synchronized boolean isDirty() { return revision != persistedRevision; }
        public synchronized void clearDirty() { markPersisted(revision); }
        public synchronized Set<String> dirty() { return Set.copyOf(dirtyGames); }

        private synchronized void markPersisted(long snapshot) {
            persistedRevision = snapshot;
            if (snapshot == revision) dirtyGames.clear();
        }
    }

    /*════════════════════════════════════════════════════
     *   StatStore  (NOW WITH ROBUST REDIS WRAPPER)
     *══════════════════════════════════════════════════*/
    private static final class StatStore {

        /* ---- static constants ---- */
        private static final long TTL = Duration.ofHours(72).toSeconds();
        private static final Gson G   = new Gson();
        private static final Type MAP = new TypeToken<Map<String,Stat>>(){}.getType();
        private final long matchTtlSeconds;
        private final Plugin plugin;
        private final Duration shutdownTimeout;
        private final PersistenceWorker writes = new PersistenceWorker();
        private final AtomicBoolean flushing = new AtomicBoolean();
        private final com.google.common.cache.Cache<UUID, String> names = CacheBuilder.newBuilder().maximumSize(20_000).build();
        private final Leaderboards leaderboards;
        private volatile boolean storedNames;
        private volatile boolean sqlReady;
        private final int retentionDays;
        private final AtomicBoolean recovering = new AtomicBoolean();

        /* ---- SQL ---- */
        private final HikariDataSource sql;

        /* ---- Redis wrapper ---- */
        private final RedisWrapper redis;
        private final SharedState shared;
        private final RecoveryJournal journal;
        private final RecoveryDatabase recoveryDb;
        private final SqlRedisRelay relay;
        private final RedisRecovery requests;
        private final com.google.common.cache.Cache<String, RecoveryJournal.Profile> bases =
                CacheBuilder.newBuilder().maximumSize(40_000).build();
        private long nextWarning;
        private String warnedState;
        private long nextCleanup;
        private int replayOffset;

        /* ---- ctor ---- */
        StatStore(Plugin plugin, Config c, int retentionDays) {
            this.plugin = plugin;
            this.retentionDays = retentionDays;
            shutdownTimeout = Duration.ofSeconds(Math.clamp(plugin.getConfig().getInt("storage.shutdown_timeout_seconds", 15), 1, 60));
            matchTtlSeconds = Duration.ofDays(retentionDays).toSeconds();
            HikariConfig hc = new HikariConfig();
            // Keep credentials out of the JDBC URL and let Connector/J handle address syntax.
            hc.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
            hc.addDataSourceProperty("serverName", c.mysqlHost());
            hc.addDataSourceProperty("port", c.mysqlPort());
            hc.addDataSourceProperty("databaseName", c.mysqlDatabase());
            hc.addDataSourceProperty("sslMode", c.mysqlSslMode());
            hc.addDataSourceProperty("connectTimeout", ConfigValues.integer(plugin.getConfig(), "mysql.connect_timeout_ms", 5000, 250, 60_000));
            hc.addDataSourceProperty("socketTimeout", ConfigValues.integer(plugin.getConfig(), "mysql.socket_timeout_ms", 8000, 250, 120_000));
            hc.setConnectionTimeout(ConfigValues.integer(plugin.getConfig(), "mysql.connection_timeout_ms", 5000, 250, 60_000));
            hc.setUsername(c.mysqlUser());
            hc.setPassword(c.mysqlPass());
            hc.setMaximumPoolSize(c.mysqlPoolSize());
            hc.setInitializationFailTimeout(-1); // Allow a cold start during a MySQL outage.
            sql = new HikariDataSource(hc);

            try {
                redis = new RedisWrapper(plugin, c.redisEnabled(), c.redisHost(), c.redisPort(), c.redisPass());
                journal = new RecoveryJournal(plugin.getDataFolder().toPath().resolve("recovery"), plugin.getLogger());
                recoveryDb = new RecoveryDatabase(sql);
                relay = new SqlRedisRelay(sql);
                String namespace = plugin.getConfig().getString("storage.recovery_namespace", "").strip();
                requests = new RedisRecovery(redis, namespace.isEmpty() ? c.mysqlDatabase() : namespace);
                shared = new SharedState(redis, sql, c.sqlSharedState(), c.redisFallbackToMysql(), relay, () -> sqlReady, plugin.getLogger());
                leaderboards = new Leaderboards(sql, redis, plugin.getLogger(), () -> storedNames, () -> sqlReady);
                prepareSql();
                warnIfDegraded();
            } catch (RuntimeException e) {
                sql.close();
                throw e;
            }
        }

        private HikariDataSource getSql() {
            return sql;
        }

        private RedisWrapper getRedis() {
            return redis;
        }

        private record Score(Leaderboards.Board board, long value) {}
        private record Snapshot(String json, long revision, List<Score> scores) {}

        MainPlayer loadMain(UUID id) {
            String json = loadProfileJson(id, "main");
            return json == null || "{}".equals(json.strip()) ? new MainPlayer(id) : MainPlayer.fromJson(id, json);
        }

        PlayerStat load(UUID id) {
            var stats = new PlayerStat(id);
            String json = loadProfileJson(id, "stats");
            if (json != null) {
                Map<String, Stat> loaded = G.fromJson(json, MAP);
                if (loaded != null) {
                    loaded.forEach((game, value) -> {
                        if (game != null && value != null) stats.games.put(game, value);
                    });
                }
            }
            return stats;
        }

        private String loadProfileJson(UUID id, String column) {
            String key = column + ":" + id;
            var pending = journal.latest(key);
            if (pending != null) return remember(column, id, new RecoveryJournal.Profile(pending.json()));
            if (sqlReady) {
                try {
                    return remember(column, id, new RecoveryJournal.Profile(loadSqlProfile(id, column)));
                } catch (SQLException failure) {
                    noteSqlFailure(failure);
                }
            }
            var asked = requests.latest(key);
            if (asked != null) return remember(column, id, new RecoveryJournal.Profile(asked.json()));
            var local = journal.readProfile(column, id);
            if (local != null) {
                bases.put(key, local);
                return local.json();
            }
            String remote = redis.withJedis(j -> j.get(key));
            return remote == null ? null : remember(column, id, new RecoveryJournal.Profile(remote));
        }

        private String remember(String column, UUID id, RecoveryJournal.Profile profile) {
            bases.put(column + ":" + id, profile);
            journal.rememberProfile(column, id, profile);
            return profile.json();
        }

        private String loadSqlProfile(UUID id, String column) throws SQLException {
            try (var connection = sql.getConnection();
                 var statement = connection.prepareStatement("SELECT " + column + " FROM player_stats WHERE uuid=?")) {
                statement.setString(1, id.toString());
                try (var result = statement.executeQuery()) {
                    return result.next() ? result.getString(1) : null;
                }
            }
        }

        private void prepareSql() {
            try {
                storedNames = setupDatabase(plugin, retentionDays);
                sqlReady = true;
                plugin.getLogger().info("[Storage] MySQL is ready. Queued writes will be replayed.");
            } catch (IllegalStateException failure) {
                sqlReady = false;
            }
            if (sqlReady) prepareOptionalTables();
        }

        private void prepareOptionalTables() {
            try {
                if (!recoveryDb.receiptsAvailable()) recoveryDb.prepare();
            } catch (SQLException failure) {
                plugin.getLogger().warning("[Recovery] Could not create the replay receipt table (" + failure.getSQLState() + ").");
            }
            try {
                if (redis.isEnabled() && !relay.available()) relay.prepare();
            } catch (SQLException failure) {
                plugin.getLogger().warning("[Recovery] Could not create the Redis relay table (" + failure.getSQLState() + ").");
            }
            try {
                shared.prepareSql();
            } catch (SQLException failure) {
                plugin.getLogger().warning("[Recovery] Could not create the experimental shared keys table (" + failure.getSQLState() + ").");
            }
        }

        private boolean verifySql() {
            try (var connection = sql.getConnection()) {
                return connection.isValid(2);
            } catch (SQLException failure) {
                return false;
            }
        }

        private void noteSqlFailure(SQLException failure) {
            String state = failure.getSQLState();
            if (failure instanceof SQLTransientConnectionException || (state != null && state.startsWith("08"))) {
                sqlReady = false;
            }
        }

        private void recover() {
            if (!recovering.compareAndSet(false, true)) return;
            try {
                if (sqlReady) sqlReady = verifySql();
                if (!sqlReady) prepareSql();
                if (redis.isEnabled()) redis.withJedis(Jedis::ping); // Keep reconnecting with network features switched off.
                if (!sqlReady) flushAllAsync();
                replayLocal();
                if (sqlReady && redis.isHealthy()) {
                    for (var write : requests.pending()) replay(write);
                    drainRedisRelay();
                }
                if (sqlReady && System.nanoTime() >= nextCleanup) {
                    nextCleanup = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                    prepareOptionalTables();
                    shared.purgeExpired();
                }
                warnIfDegraded();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "[Recovery] A recovery pass failed. Pending records were retained.", failure);
            } finally {
                recovering.set(false);
            }
        }

        private void replayLocal() {
            var pending = journal.pending();
            if (pending.isEmpty()) return;
            int start = Math.floorMod(replayOffset, pending.size());
            int count = Math.min(64, pending.size());
            replayOffset = start + count;
            for (int step = 0; step < count; step++) {
                var write = pending.get((start + step) % pending.size());
                if (sqlReady && replay(write)) journal.acknowledge(write);
                else if (!sqlReady && requests.acknowledged(write)) journal.acknowledge(write);
                else if (!sqlReady) requests.ask(write);
            }
        }

        private boolean replay(RecoveryJournal.Write write) {
            if (!sqlReady) return false;
            try {
                var result = recoveryDb.apply(write, storedNames, true);
                if (result == RecoveryDatabase.Result.CONFLICT) return false;
                if (result == RecoveryDatabase.Result.WRITTEN) publishCache(write, write.json(), List.of());
                requests.acknowledge(write);
                return true;
            } catch (SQLException failure) {
                noteSqlFailure(failure);
                return false;
            }
        }

        private void drainRedisRelay() {
            if (!relay.available()) return;
            try {
                for (var job : relay.requests()) {
                    if (!redis.isHealthy() || !sqlReady) break;
                    if (!relay.claim(job)) continue;
                    boolean sent;
                    if (job.key().startsWith("health:")) {
                        sent = Boolean.TRUE.equals(shared.publishRelayedHealth(job.key().substring(7), job.payload()));
                    } else {
                        sent = publishRelayedCache(job.key());
                    }
                    if (sent) relay.finish(job);
                }
            } catch (SQLException failure) {
                noteSqlFailure(failure);
            }
        }

        private boolean publishRelayedCache(String key) throws SQLException {
            String[] parts = key.split(":", 2);
            if (parts.length != 2) return true;
            String json;
            switch (parts[0]) {
                case "main", "stats" -> {
                    if (!parts[1].matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) return true;
                    json = loadSqlProfile(UUID.fromString(parts[1]), parts[0]);
                }
                case "match" -> {
                    if (!parts[1].matches("[0-9A-Za-z]{1,8}")) return true;
                    json = loadSqlMatch(parts[1]);
                }
                default -> { return true; }
            }
            if (json == null) return true;
            long ttl = parts[0].equals("match") ? matchTtlSeconds : TTL;
            return Boolean.TRUE.equals(redis.withJedis(j -> {
                j.setex(key, ttl, json);
                return true;
            }));
        }

        private void warnIfDegraded() {
            long now = System.nanoTime();
            String state = !sqlReady ? (redis.isHealthy() ? "mysql" : "standalone")
                    : redis.isEnabled() && !redis.isHealthy() ? "redis" : "ready";
            if (state.equals(warnedState) && now < nextWarning) return;
            warnedState = state;
            nextWarning = now + TimeUnit.SECONDS.toNanos(30);
            if (state.equals("standalone")) {
                plugin.getLogger().severe("[Recovery] MySQL and Redis are unavailable. Running standalone and saving pending changes under plugins/Core/recovery. Restore MySQL before moving players to other servers.");
            } else if (state.equals("mysql")) {
                plugin.getLogger().severe("[Recovery] MYSQL DOWN. Urgent writes are queued on disk and in Redis. Any instance with MySQL access can save them. Restore MySQL access to drain the queue.");
            } else if (state.equals("redis")) {
                plugin.getLogger().warning("[Recovery] REDIS DOWN. Gameplay is local and durable data is in MySQL. Asking a healthy peer to republish supported Redis keys. Restore Redis for global features.");
            }
            int pending = journal.pending().size();
            if (sqlReady && pending != 0) {
                plugin.getLogger().warning("[Recovery] " + pending + " local writes are awaiting replay. Writes based on older or unknown data are retained if MySQL already has newer data.");
            }
        }

        boolean saveMain(MainPlayer profile, boolean updateRedis) {
            synchronized (profile.writeLock) {
                Snapshot snapshot;
                synchronized (profile) {
                    if (!profile.isDirty()) return true;
                    snapshot = new Snapshot(profile.toJson(), profile.revision(),
                            List.of(new Score(Leaderboards.Board.overall(), profile.getOverallRankedStats().getElo())));
                }
                if (!writeProfile(profile.uuid(), "main", snapshot, updateRedis)) return false;
                profile.markPersisted(snapshot.revision);
                return true;
            }
        }

        boolean save(PlayerStat stats, boolean updateRedis) {
            synchronized (stats.writeLock) {
                Snapshot snapshot;
                synchronized (stats) {
                    if (!stats.isDirty()) return true;
                    List<Score> scores = new ArrayList<>();
                    for (String game : stats.dirtyGames) {
                        Stat row = stats.games.get(game);
                        scores.add(new Score(Leaderboards.Board.game(game, Field.KILLS), row.kills()));
                        scores.add(new Score(Leaderboards.Board.game(game, Field.DEATHS), row.deaths()));
                        scores.add(new Score(Leaderboards.Board.game(game, Field.WINS), row.wins()));
                        scores.add(new Score(Leaderboards.Board.game(game, Field.STREAK), row.winStreak()));
                        scores.add(new Score(Leaderboards.Board.game(game, Field.ROUNDS), row.roundsPlayed()));
                        scores.add(new Score(Leaderboards.Board.game(game, Field.TIME), row.timePlayed()));
                    }
                    snapshot = new Snapshot(G.toJson(stats.games), stats.revision, scores);
                }
                if (!writeProfile(stats.uuid(), "stats", snapshot, updateRedis)) return false;
                stats.markPersisted(snapshot.revision);
                return true;
            }
        }

        private boolean writeProfile(UUID id, String column, Snapshot snapshot, boolean updateRedis) {
            String key = column + ":" + id;
            var base = bases.getIfPresent(key);
            if (base == null) base = journal.readProfile(column, id);
            var write = journal.newWrite(column, id.toString(), base, snapshot.json, names.getIfPresent(id));
            boolean saved = commitOrQueue(write, updateRedis, snapshot.scores);
            if (saved) remember(column, id, new RecoveryJournal.Profile(snapshot.json));
            return saved;
        }

        private boolean commitOrQueue(RecoveryJournal.Write write, boolean updateRedis, List<Score> scores) {
            if (sqlReady && journal.latest(write.key()) == null) {
                try {
                    var result = recoveryDb.apply(write, storedNames, false);
                    if (result != RecoveryDatabase.Result.CONFLICT) {
                        if (updateRedis) publishCache(write, write.json(), scores);
                        return true;
                    }
                } catch (SQLException failure) {
                    noteSqlFailure(failure);
                }
            }

            boolean onDisk = journal.enqueue(write);
            boolean offered = requests.ask(write);
            return onDisk || offered;
        }

        private void publishCache(RecoveryJournal.Write write, String json, List<Score> scores) {
            if (!redis.isEnabled()) return;
            boolean match = write.type().equals("match");
            Boolean updated = redis.withJedis(j -> {
                try (var pipeline = j.pipelined()) {
                    pipeline.setex(write.key(), match ? matchTtlSeconds : TTL, json);
                    if (!match && write.name() != null) pipeline.hset(Leaderboards.NAMES, write.subject(), write.name());
                    if (!scores.isEmpty()) {
                        UUID player = UUID.fromString(write.subject());
                        for (Score score : scores) Leaderboards.index(pipeline, score.board, player, score.value);
                    }
                    pipeline.sync();
                }
                return true;
            });
            if (updated == null && sqlReady && !redis.isHealthy()) {
                try {
                    relay.offer(write.key(), "", 300);
                } catch (SQLException failure) {
                    noteSqlFailure(failure);
                }
            }
        }

        /* ensures table exists on first start */
        private boolean setupDatabase(Plugin plugin, int expiryDays) {
            String[] ddlStatements = {
                """
                CREATE TABLE IF NOT EXISTS player_stats (
                  uuid  CHAR(36) PRIMARY KEY,
                  player_name VARCHAR(64),
                  stats JSON      NOT NULL,
                  main JSON       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """,
                """
                CREATE TABLE IF NOT EXISTS ranked_matches (
                  uuid CHAR(8) PRIMARY KEY,
                  player1 CHAR(36) NOT NULL,
                  player2 CHAR(36) NOT NULL,
                  data JSON NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_player1 (player1),
                  INDEX idx_player2 (player2)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """
            };

            boolean hasPlayerName;
            try (Connection c = sql.getConnection();
                 Statement st = c.createStatement()) {
                for (String sqlStmt : ddlStatements) {
                    st.executeUpdate(sqlStmt);
                }
                try (var columns = c.getMetaData().getColumns(c.getCatalog(), null, "player_stats", "player_name")) {
                    hasPlayerName = columns.next();
                }
                if (!hasPlayerName) {
                    try {
                        st.executeUpdate("ALTER TABLE player_stats ADD COLUMN player_name VARCHAR(64)");
                        hasPlayerName = true;
                    } catch (SQLException migration) {
                        switch (migration.getErrorCode()) {
                            case 1060 -> hasPlayerName = true; // Another instance already added it.
                            case 1044, 1142 -> plugin.getLogger().warning("[Stats] Grant ALTER on player_stats to persist leaderboard names in MySQL.");
                            default -> throw migration;
                        }
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not initialize MySQL tables.", e);
            }

            // Expire SQL records using the same retention window as the Redis cache.
            try (Connection c = sql.getConnection();
                 PreparedStatement st = c.prepareStatement(
                         "DELETE FROM ranked_matches WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
                st.setInt(1, expiryDays);
                st.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Stats] Could not delete expired ranked matches.");
            }
            return hasPlayerName;
        }



        /*────────────────── MATCH STORAGE ──────────────────*/
        void saveMatch(RankedMatchRecord record) {
            var write = journal.newWrite("match", record.getMatchId(), null, G.toJson(record), null);
            if (!commitOrQueue(write, true, List.of())) {
                plugin.getLogger().severe("[Recovery] Could not save or queue ranked match " + record.getMatchId());
            }
        }

        RankedMatchRecord loadMatch(String matchId) {
            String key = "match:" + matchId;
            var pending = journal.latest(key);
            if (pending == null) pending = requests.latest(key);
            String json = pending == null ? redis.withJedis(j -> j.get(key)) : pending.json();
            if (json == null && sqlReady) {
                try {
                    json = loadSqlMatch(matchId);
                } catch (SQLException failure) {
                    noteSqlFailure(failure);
                }
            }
            return json == null ? null : G.fromJson(json, RankedMatchRecord.class);
        }

        private String loadSqlMatch(String matchId) throws SQLException {
            try (Connection connection = sql.getConnection();
                 PreparedStatement query = connection.prepareStatement("SELECT data FROM ranked_matches WHERE uuid=?")) {
                query.setString(1, matchId);
                try (ResultSet result = query.executeQuery()) {
                    return result.next() ? result.getString(1) : null;
                }
            }
        }

        List<RankedMatchRecord> loadRecent(UUID playerId, int limit) {
            Map<String, RankedMatchRecord> matches = new HashMap<>();
            if (sqlReady) {
                try (var connection = sql.getConnection();
                     var query = connection.prepareStatement(
                             "SELECT data FROM ranked_matches WHERE player1=? OR player2=? ORDER BY created_at DESC LIMIT ?")) {
                    query.setString(1, playerId.toString());
                    query.setString(2, playerId.toString());
                    query.setInt(3, limit);
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) {
                            var match = G.fromJson(rows.getString(1), RankedMatchRecord.class);
                            matches.put(match.getMatchId(), match);
                        }
                    }
                } catch (SQLException failure) {
                    noteSqlFailure(failure);
                }
            }
            for (var write : journal.pending()) {
                if (!write.type().equals("match")) continue;
                var match = G.fromJson(write.json(), RankedMatchRecord.class);
                if (playerId.equals(match.getPlayer1()) || playerId.equals(match.getPlayer2())) {
                    matches.putIfAbsent(match.getMatchId(), match);
                }
            }
            return matches.values().stream().sorted(Comparator.comparingLong(RankedMatchRecord::getStartTime).reversed())
                    .limit(Math.max(0, limit)).toList();
        }

        UUID findRecentMatchPlayer(String name) {
            if (!sqlReady) return null;
            // The match names are present in existing telemetry records, so this also covers
            // matches stored before network matchmaking without a profile schema change.
            String query = """
                    SELECT CASE
                        WHEN LOWER(JSON_UNQUOTE(JSON_EXTRACT(data, '$.player1Name'))) = ? THEN player1
                        ELSE player2
                    END AS player_id
                    FROM ranked_matches
                    WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? SECOND)
                        AND (LOWER(JSON_UNQUOTE(JSON_EXTRACT(data, '$.player1Name'))) = ?
                            OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(data, '$.player2Name'))) = ?)
                    ORDER BY created_at DESC LIMIT 1
                    """;
            try (Connection connection = sql.getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, name);
                statement.setLong(2, matchTtlSeconds);
                statement.setString(3, name);
                statement.setString(4, name);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? UUID.fromString(result.getString(1)) : null;
                }
            } catch (SQLException e) {
                return null;
            }
        }
    }

    /*──────────────────────────────────────── RedisWrapper ────────────────*/


    public static final class RedisWrapper implements AutoCloseable {
        private final Plugin plugin;
        private final boolean enabled;
        private final String host;
        private final int port;
        private final String password;
        private volatile JedisPool pool;
        private final JedisPoolConfig cfg = new JedisPoolConfig();
        private final int timeoutMillis;

        private volatile boolean healthy = false;
        private volatile int retryAttempts = 0;
        private long nextAttemptTime;
        private boolean reconnecting;
        private boolean closed;

        RedisWrapper(Plugin plugin, boolean enabled, String host, int port, String pwd) {
            this.plugin = plugin;
            this.enabled = enabled;
            this.host = host;
            this.port = port;
            this.password = pwd;

            var config = plugin.getConfig();
            int poolSize = ConfigValues.integer(config, "redis.pool_size", 16, 1, 128);
            cfg.setMaxTotal(poolSize);
            cfg.setMinIdle(Math.min(poolSize, ConfigValues.integer(config, "redis.min_idle", 2, 0, 128)));
            timeoutMillis = ConfigValues.integer(config, "redis.timeout_ms", 8000, 250, 60_000);
            cfg.setTestOnBorrow(false);
            cfg.setTestOnReturn(false);
            cfg.setTestWhileIdle(true);
            cfg.setMaxWait(Duration.ofMillis(ConfigValues.seconds(config, "redis.borrow_timeout_seconds", 10, 1, 60)));
            cfg.setMinEvictableIdleTime(Duration.ofMinutes(5));
            cfg.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
            cfg.setNumTestsPerEvictionRun(3);

            if (enabled) init(); // first attempt
        }

        private void init() {
            JedisPool candidate = null;
            Exception failure = null;
            try {
                boolean sslEnabled = plugin.getConfig().getBoolean("redis.ssl");
                if (sslEnabled) {
                    SSLContext sslContext = getSSLContext();
                    SSLParameters sslParameters = new SSLParameters();
                    sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                    candidate = new JedisPool(cfg, host, port, timeoutMillis, password, true,
                            sslContext.getSocketFactory(), sslParameters, null);
                } else {
                    candidate = new JedisPool(cfg, host, port, timeoutMillis, password);
                }

                try (Jedis j = candidate.getResource()) {
                    if (!"PONG".equalsIgnoreCase(j.ping())) throw new RuntimeException("No PONG reply");
                }

            } catch (Exception e) {
                failure = e;
            }

            JedisPool old;
            boolean ready;
            synchronized (this) {
                ready = !closed && failure == null;
                old = pool;
                pool = ready ? candidate : null;
                healthy = ready;
                retryAttempts = ready ? 0 : retryAttempts + 1;
                reconnecting = false;
                nextAttemptTime = System.currentTimeMillis() + 5000L * Math.min(6, Math.max(1, retryAttempts));
            }
            if (old != null) old.close();
            if (!ready && candidate != null) candidate.close();
            if (ready) {
                plugin.getLogger().info("[Stats] Redis pool ready.");
            } else if (failure != null && plugin.isEnabled()) {
                plugin.getLogger().warning("[Stats] Redis connection attempt " + retryAttempts + " failed ("
                        + failure.getClass().getSimpleName() + "). Check the endpoint, authentication and TLS settings.");
            }
        }

        private SSLContext getSSLContext() throws Exception {
            String customCa = plugin.getConfig().getString("redis.ca_file", "").strip();
            var path = plugin.getDataFolder().toPath();
            if (customCa.isEmpty()) {
                path = path.resolve("certs/ca.crt");
                if (!java.nio.file.Files.isRegularFile(path)) return SSLContext.getDefault();
            } else {
                path = path.resolve(customCa);
            }

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                trustStore.setCertificateEntry("ca", cf.generateCertificate(fis));
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        }

        private synchronized void ensure() {
            long now = System.currentTimeMillis();
            if (!healthy && !closed && !reconnecting && now >= nextAttemptTime && plugin.isEnabled()) {
                reconnecting = true;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::init);
            }
        }

        public <R> R withJedis(Function<Jedis, R> fn) {
            if (!enabled) return null;
            if (!healthy) ensure();
            if (!healthy) return null;

            JedisPool current = pool;
            if (current == null) return null;
            try (Jedis j = current.getResource()) {
                return fn.apply(j);
            } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
                if (markInterrupted(current)) {
                    plugin.getLogger().warning("[Stats] Redis connection interrupted. Retrying in the background.");
                }
                return null;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[Stats] Redis operation failed (" + e.getClass().getSimpleName() + ").");
                return null;
            }
        }

        private synchronized boolean markInterrupted(JedisPool current) {
            if (current != pool || !healthy) return false;
            healthy = false;
            return true;
        }

        @Override
        public void close() {
            JedisPool current;
            synchronized (this) {
                closed = true;
                healthy = false;
                current = pool;
                pool = null;
            }
            if (current != null) current.close();
        }

        public boolean isHealthy() {
            return healthy;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
