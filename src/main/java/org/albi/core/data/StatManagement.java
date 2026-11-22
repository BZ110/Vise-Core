/*─────────────────────────────────────────────────────────────────────────────
 *  org.albi.core.data.StatManagement   (fixed Redis handling)
 *───────────────────────────────────────────────────────────────────────────*/
package org.albi.core.data;

import com.google.gson.Gson;
import com.google.common.cache.CacheBuilder;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import java.util.logging.Level;
import java.util.function.Function;

public final class StatManagement {

    /*─────────────────────────────────────────────────────────────────────────*/
    /*  PUBLIC FACADE (unchanged)                                             */
    /*─────────────────────────────────────────────────────────────────────────*/
    public static void init(Plugin plugin, Config cfg) {
        int retentionDays = Math.max(1, plugin.getConfig().getInt("ranked.history_expiry_days", 7));
        INTERNAL.store = new StatStore(plugin, cfg, retentionDays);
    }

    public static void shutdown() {
        StatStore store = INTERNAL.store;
        if (store == null) return;
        try {
            if (!store.writes.finish(() -> flush(store, true), store.shutdownTimeout)) {
                store.plugin.getLogger().warning("[Stats] Final MySQL flush exceeded the shutdown timeout. Some writes may be incomplete.");
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
            store.plugin.getLogger().warning("[Stats] Some player data could not be saved to MySQL at shutdown (" + failed + " profiles).");
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
        return store != null && persist(store, stat, profile, true);
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

    public static void saveMatchRecord(org.albi.core.modules.Duels.ranked.RankedMatchRecord record) {
        StatStore store = INTERNAL.store;
        if (store != null) store.writes.submit(() -> store.saveMatch(record));
    }

    public static org.albi.core.modules.Duels.ranked.RankedMatchRecord loadMatchRecord(String matchId) {
        return INTERNAL.store.loadMatch(matchId);
    }

    public static List<org.albi.core.modules.Duels.ranked.RankedMatchRecord> loadRecentMatches(UUID playerId, int limit) {
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
            String mysqlSslMode, int mysqlPoolSize, String redisHost, int redisPort, String redisPass) {

        public static Config from(ConfigurationSection yaml) {
            String sslMode = yaml.getString("mysql.ssl_mode", "").strip().toUpperCase(Locale.ROOT);
            if (sslMode.isEmpty()) sslMode = yaml.getBoolean("mysql.ssl") ? "REQUIRED" : "DISABLED";
            if (!Set.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY").contains(sslMode)) {
                throw new IllegalArgumentException("mysql.ssl_mode must be a Connector/J TLS mode.");
            }
            String redisPassword = ConfigValues.envOrYaml(yaml, "redis.password", "VISE_REDIS_PASSWORD");
            int poolSize = yaml.getInt("mysql.pool_size", 20);
            if (poolSize < 1) throw new IllegalArgumentException("mysql.pool_size must be positive.");

            return new Config(
                    required(yaml, "mysql.host", "VISE_MYSQL_HOST"),
                    port(yaml, "mysql.port", "VISE_MYSQL_PORT", 3306),
                    required(yaml, "mysql.database", "VISE_MYSQL_DATABASE"),
                    required(yaml, "mysql.user", "VISE_MYSQL_USER"),
                    ConfigValues.envOrYaml(yaml, "mysql.password", "VISE_MYSQL_PASSWORD"),
                    sslMode, poolSize,
                    required(yaml, "redis.host", "VISE_REDIS_HOST"),
                    port(yaml, "redis.port", "VISE_REDIS_PORT", 6379),
                    redisPassword.isEmpty() ? null : redisPassword);
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
        private final boolean storedNames;

        /* ---- SQL ---- */
        private final HikariDataSource sql;

        /* ---- Redis wrapper ---- */
        private final RedisWrapper redis;

        /* ---- ctor ---- */
        StatStore(Plugin plugin, Config c, int retentionDays) {
            this.plugin = plugin;
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
            sql = new HikariDataSource(hc);

            try {
                storedNames = setupDatabase(plugin, retentionDays);
                redis = new RedisWrapper(plugin, c.redisHost(), c.redisPort(), c.redisPass());
                leaderboards = new Leaderboards(sql, redis, plugin.getLogger(), storedNames);
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
            return json == null ? new MainPlayer(id) : MainPlayer.fromJson(id, json);
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
            // MySQL is authoritative even when the server was stopped while Redis was unavailable.
            SQLException databaseError = null;
            try (var connection = sql.getConnection();
                 var statement = connection.prepareStatement("SELECT " + column + " FROM player_stats WHERE uuid=?")) {
                statement.setString(1, id.toString());
                try (var result = statement.executeQuery()) {
                    if (result.next()) return result.getString(1);
                }
            } catch (SQLException failure) {
                databaseError = failure;
                plugin.getLogger().warning("[Stats] Could not load " + column + " for " + id + " from MySQL. Trying Redis.");
            }
            String cached = redis.withJedis(j -> j.get(column + ":" + id));
            if (cached == null && databaseError != null) throw new IllegalStateException("Player data unavailable", databaseError);
            return cached;
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
            String playerName = names.getIfPresent(id);
            String other = column.equals("main") ? "stats" : "main";
            String upsert = storedNames
                    ? "INSERT INTO player_stats(uuid, " + column + ", " + other + ", player_name) "
                        + "VALUES(?, ?, '{}', ?) ON DUPLICATE KEY UPDATE " + column + "=?, player_name=COALESCE(?, player_name)"
                    : "INSERT INTO player_stats(uuid, " + column + ", " + other + ") "
                        + "VALUES(?, ?, '{}') ON DUPLICATE KEY UPDATE " + column + "=?";
            try (var connection = sql.getConnection(); var statement = connection.prepareStatement(upsert)) {
                statement.setString(1, id.toString());
                statement.setString(2, snapshot.json);
                if (storedNames) {
                    statement.setString(3, playerName);
                    statement.setString(4, snapshot.json);
                    statement.setString(5, playerName);
                } else {
                    statement.setString(3, snapshot.json);
                }
                statement.executeUpdate();
            } catch (SQLException failure) {
                plugin.getLogger().warning("[Stats] Could not save " + column + " for " + id + " to MySQL (" + failure.getSQLState() + ").");
                return false;
            }

            if (updateRedis) {
                redis.withJedis(j -> {
                    try (var pipeline = j.pipelined()) {
                        pipeline.setex(column + ":" + id, (int) TTL, snapshot.json);
                        if (playerName != null) pipeline.hset(Leaderboards.NAMES, id.toString(), playerName);
                        for (Score score : snapshot.scores) Leaderboards.index(pipeline, score.board, id, score.value);
                        pipeline.sync();
                    }
                    return null;
                });
            }
            return true;
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
        void saveMatch(org.albi.core.modules.Duels.ranked.RankedMatchRecord record) {
            String key = "match:" + record.getMatchId();
            String json = G.toJson(record);

            try (Connection c = sql.getConnection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO ranked_matches(uuid, player1, player2, data) VALUES(?, ?, ?, ?)")) {
                ps.setString(1, record.getMatchId());
                ps.setString(2, record.getPlayer1().toString());
                ps.setString(3, record.getPlayer2().toString());
                ps.setString(4, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[Stats] Could not save ranked match " + record.getMatchId() + " to MySQL.");
            }
            redis.withJedis(j -> {
                j.setex(key, (int) matchTtlSeconds, json);
                return null;
            });
        }

        org.albi.core.modules.Duels.ranked.RankedMatchRecord loadMatch(String matchId) {
            String key = "match:" + matchId;

            /* 1️⃣ Redis */
            String json = redis.withJedis(j -> j.get(key));
            if (json != null) {
                return G.fromJson(json, org.albi.core.modules.Duels.ranked.RankedMatchRecord.class);
            }

            /* 2️⃣ SQL */
            try (Connection c = sql.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT data FROM ranked_matches WHERE uuid=?")) {
                ps.setString(1, matchId.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    json = rs.getString(1);
                    return G.fromJson(json, org.albi.core.modules.Duels.ranked.RankedMatchRecord.class);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        List<org.albi.core.modules.Duels.ranked.RankedMatchRecord> loadRecent(UUID playerId, int limit) {
            List<org.albi.core.modules.Duels.ranked.RankedMatchRecord> list = new ArrayList<>();
            try (Connection c = sql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT data FROM ranked_matches WHERE player1=? OR player2=? ORDER BY created_at DESC LIMIT ?")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, playerId.toString());
                ps.setInt(3, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(G.fromJson(rs.getString(1), org.albi.core.modules.Duels.ranked.RankedMatchRecord.class));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }

        UUID findRecentMatchPlayer(String name) {
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


    public static final class RedisWrapper {
        private final Plugin plugin;
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

        RedisWrapper(Plugin plugin, String host, int port, String pwd) {
            this.plugin = plugin;
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

            init(); // first attempt
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
    }
}
