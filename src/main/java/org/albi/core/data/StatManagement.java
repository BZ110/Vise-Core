/*─────────────────────────────────────────────────────────────────────────────
 *  org.albi.core.data.StatManagement   (fixed Redis handling)
 *───────────────────────────────────────────────────────────────────────────*/
package org.albi.core.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.albi.core.util.ConfigValues;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.*;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.Tuple;

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
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StatManagement {

    /*─────────────────────────────────────────────────────────────────────────*/
    /*  PUBLIC FACADE (unchanged)                                             */
    /*─────────────────────────────────────────────────────────────────────────*/
    public static void init(Plugin plugin, Config cfg) {
        int retentionDays = Math.max(1, plugin.getConfig().getInt("ranked.history_expiry_days", 7));
        INTERNAL.store = new StatStore(plugin, cfg, retentionDays);
        INTERNAL.plugin = plugin;
    }

    public static void shutdown() {
        StatStore store = INTERNAL.store;
        if (store == null) return;
        store.getRedis().close();
        store.getSql().close();
        INTERNAL.store = null;
        INTERNAL.cache.clear();
        INTERNAL.mainCache.clear();
    }
    public static CompletableFuture<PlayerStat> getAsync(UUID id, String name) {
        return CompletableFuture.supplyAsync(() ->
                INTERNAL.cache.computeIfAbsent(id, u -> INTERNAL.store.load(u, name))
        );
    }
    public static void saveAndRemove(UUID id) {
        PlayerStat ps = INTERNAL.cache.remove(id);
        if (ps != null)
            Bukkit.getScheduler().runTaskAsynchronously(
                    INTERNAL.plugin, () -> INTERNAL.store.save(ps));

        MainPlayer mp = INTERNAL.mainCache.remove(id);                     // NEW
        if (mp != null)
            Bukkit.getScheduler().runTaskAsynchronously(
                    INTERNAL.plugin, () -> INTERNAL.store.saveMain(mp));       // NEW
    }

    public static void flushAllAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(INTERNAL.plugin, () -> {
            INTERNAL.cache.values().forEach(INTERNAL.store::save);
            INTERNAL.mainCache.values().forEach(INTERNAL.store::saveMain);
        });
    }

    public static String getName(UUID id) {
        return INTERNAL.store.getRedis().withJedis(j -> j.hget("lb:names", id.toString()));
    }

    public static void resetWinStreak(UUID id, String game) {
        getAsync(id, "<unknown>").thenAccept(a -> a.resetWinStreak(game));
    }
    public static List<LBRow> leaderboard(String game, Field f, int n) {
        return INTERNAL.store.top(game, f.redis, n)
                .stream().map(e -> new LBRow(e.getKey(), e.getValue())).toList();
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
        return CompletableFuture.supplyAsync(() ->
                INTERNAL.mainCache.computeIfAbsent(p.getUniqueId(), INTERNAL.store::loadMain)
        );
    }

    public static MainPlayer getMain(org.bukkit.entity.Player p) {
        return getMain(p.getUniqueId());
    }

    public static void saveMatchRecord(org.albi.core.modules.Duels.ranked.RankedMatchRecord record) {
        Bukkit.getScheduler().runTaskAsynchronously(INTERNAL.plugin, () -> INTERNAL.store.saveMatch(record));
    }

    public static org.albi.core.modules.Duels.ranked.RankedMatchRecord loadMatchRecord(String matchId) {
        return INTERNAL.store.loadMatch(matchId);
    }

    public static List<org.albi.core.modules.Duels.ranked.RankedMatchRecord> loadRecentMatches(UUID playerId, int limit) {
        return INTERNAL.store.loadRecent(playerId, limit);
    }


    /*────────────────── internal glue ──────────────────*/
    private static final class INTERNAL {
        private static StatStore store;
        private static Plugin    plugin;

        private static final Map<UUID, PlayerStat> cache     = new ConcurrentHashMap<>();
        private static final Map<UUID, MainPlayer> mainCache = new ConcurrentHashMap<>();   // NEW
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
        KILLS  ("kills"),
        DEATHS ("deaths"),
        WINS   ("wins"),
        STREAK ("streak"),
        ROUNDS ("rounds"),
        TIME   ("time");
        public final String redis;
        Field(String r) { this.redis = r; }
    }
    public record LBRow(UUID uuid,long score){}

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
        private final UUID uuid; private final Map<String,Stat> map=new HashMap<>();
        private final Set<String> dirtyGames=new HashSet<>(); private boolean dirty;
        public PlayerStat(UUID id){uuid=id;}
        public Stat game(String g){dirty=true;return map.computeIfAbsent(g,k->new Stat());}
        public void addKill(String g) { game(g).addKills(1);  dirtyGames.add(g); }
        public void addDeath(String g){ game(g).addDeaths(1); dirtyGames.add(g); }

        /* new: addWin = +wins and +winstreak */
        public void addWin(String g)  {
            Stat s = game(g);
            s.addWin();
            dirtyGames.add(g);
        }

        public void addRound(String g){ game(g).addRound();   dirtyGames.add(g); }
        public void addTime(String g,int s){ game(g).addTime(s); dirtyGames.add(g); }

        public void resetWinStreak(String g){
            game(g).resetWinStreak();
            dirtyGames.add(g);
        }
        public Map<String,Stat> all(){return map;} public UUID uuid(){return uuid;}
        public boolean isDirty(){return dirty;}  public void clearDirty(){dirty=false;dirtyGames.clear();}
        public Set<String> dirty(){return dirtyGames;}
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

        /* ---- SQL ---- */
        private final HikariDataSource sql;

        /* ---- Redis wrapper ---- */
        private final RedisWrapper redis;

        /* ---- ctor ---- */
        StatStore(Plugin plugin, Config c, int retentionDays) {
            matchTtlSeconds = Duration.ofDays(retentionDays).toSeconds();
            HikariConfig hc = new HikariConfig();
            // Keep credentials out of the JDBC URL and let Connector/J handle address syntax.
            hc.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
            hc.addDataSourceProperty("serverName", c.mysqlHost());
            hc.addDataSourceProperty("port", c.mysqlPort());
            hc.addDataSourceProperty("databaseName", c.mysqlDatabase());
            hc.addDataSourceProperty("sslMode", c.mysqlSslMode());
            hc.setUsername(c.mysqlUser());
            hc.setPassword(c.mysqlPass());
            hc.setMaximumPoolSize(c.mysqlPoolSize());
            sql = new HikariDataSource(hc);

            try {
                setupDatabase(plugin, retentionDays);
                redis = new RedisWrapper(plugin, c.redisHost(), c.redisPort(), c.redisPass());
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

        /*────────────────── MAIN‐PROFILE LOAD ──────────────────*/
        MainPlayer loadMain(UUID id) {
            String key = "main:" + id;

            /* 1️⃣  Redis cache */
            String json = redis.withJedis(j -> j.get(key));
            if (json != null) {
                redis.withJedis(j -> { j.expire(key, (int) TTL); return null; });
                return MainPlayer.fromJson(id, json);
            }

            /* 2️⃣  SQL fallback */
            try (Connection c = sql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT main FROM player_stats WHERE uuid=?")) {
                ps.setString(1, id.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    json = rs.getString(1);
                    final String cacheIt = json;
                    redis.withJedis(j -> { j.setex(key, (int) TTL, cacheIt); return null; });
                    return MainPlayer.fromJson(id, json);
                }
            } catch (SQLException ignored) { }
            return new MainPlayer(id);      // brand‑new profile
        }

        /*────────────────── MAIN‐PROFILE SAVE ──────────────────*/
        void saveMain(MainPlayer mp) {
            if (!mp.isDirty()) return;

            String key  = "main:" + mp.uuid();
            String json = mp.toJson();

            /* 1️⃣  Redis */
            redis.withJedis(j -> {
                j.setex(key, (int) TTL, json);
                // Sync overall Elo to leaderboard
                if (mp.getOverallRankedStats() != null) {
                    j.zadd("lb:ranked:elo", (double) mp.getOverallRankedStats().getElo(), mp.uuid().toString());
                }
                // Store name for leaderboard lookups
                Player p = Bukkit.getPlayer(mp.uuid());
                if (p != null) {
                    j.hset("lb:names", mp.uuid().toString(), p.getName());
                }
                return null;
            });

            /* 2️⃣  SQL (upsert) */
            try (Connection c = sql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         """
                         INSERT INTO player_stats(uuid,stats,main)
                         VALUES(?, '{}', ?)                       -- '{}' placeholder for stats if new row
                         ON DUPLICATE KEY UPDATE main=VALUES(main)
                         """)) {
                ps.setString(1, mp.uuid().toString());
                ps.setString(2, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            mp.clearDirty();
        }


        /* ensures table exists on first start */
        private void setupDatabase(Plugin plugin, int expiryDays) {
            String[] ddlStatements = {
                """
                CREATE TABLE IF NOT EXISTS player_stats (
                  uuid  CHAR(36) PRIMARY KEY,
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

            try (Connection c = sql.getConnection();
                 Statement st = c.createStatement()) {
                for (String sqlStmt : ddlStatements) {
                    st.executeUpdate(sqlStmt);
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
        }



        /* ───── load ───── */
        PlayerStat load(UUID id,String name){
            String key="stats:"+id;
            String js = redis.withJedis(j -> j.get(key));
            if(js!=null){
                redis.withJedis(j -> { j.expire(key,(int)TTL); return null;});
                return inflate(id,js);
            }
            /* cache miss -> SQL */
            try(Connection c=sql.getConnection(); PreparedStatement ps=c.prepareStatement(
                    "SELECT stats FROM player_stats WHERE uuid=?")){
                ps.setString(1,id.toString()); ResultSet rs=ps.executeQuery();
                if(rs.next()){ js=rs.getString(1);
                    String finalJs = js;
                    redis.withJedis(j -> { j.setex(key,(int)TTL, finalJs); return null;});
                    return inflate(id,js);
                }
            }catch(SQLException ignored){}
            return new PlayerStat(id);
        }

        /* ───── save ───── */
        void save(PlayerStat p) {
            if (!p.isDirty()) return;

            String key = "stats:" + p.uuid();
            Map<String, Stat> clean = p.all().entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            String js = G.toJson(clean);

            redis.withJedis(j -> {
                j.setex(key, (int) TTL, js);
                for (String g : p.dirty()) {
                    Stat s = p.all().get(g);

                    /* every metric → its own sorted-set */
                    zset(j, g, "kills",   s.kills(),        p.uuid());
                    zset(j, g, "deaths",  s.deaths(),       p.uuid());
                    zset(j, g, "wins",    s.wins(),         p.uuid());
                    zset(j, g, "streak",  s.winStreak(),    p.uuid());
                    zset(j, g, "rounds",  s.roundsPlayed(), p.uuid());
                    zset(j, g, "time",    s.timePlayed(),   p.uuid());
                }
                return null;
            });

            /* SQL upsert unchanged */
            try (Connection c = sql.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO player_stats(uuid,stats,main) VALUES(?,?, '{}') " +
                                 "ON DUPLICATE KEY UPDATE stats=?")) {
                ps.setString(1, p.uuid().toString());
                ps.setString(2, js);
                ps.setString(3, js);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }

            p.clearDirty();
        }


        /* ───── leaderboard ───── */
        List<Map.Entry<UUID,Long>> top(String g,String f,int n){
            return redis.withJedis(j -> {
                List<Map.Entry<UUID,Long>> list=new ArrayList<>();
                j.zrevrangeWithScores(lb(g,f),0,n-1)
                        .forEach(t->list.add(Map.entry(UUID.fromString(t.getElement()),(long)t.getScore())));
                return list;
            });
        }

        /* ---- helpers ---- */
        private static PlayerStat inflate(UUID id,String js){
            PlayerStat p=new PlayerStat(id);
            p.all().putAll(G.fromJson(js,MAP));
            p.clearDirty();
            return p;
        }
        private static void zset(Jedis j,String g,String f,long score,UUID uuid){
            String z=lb(g,f);
            long updated = j.zadd(z, score, uuid.toString(), ZAddParams.zAddParams().xx().ch());
            if (updated == 0) {
                j.zadd(z, score, uuid.toString());   // first‑time insert
            }

            j.zremrangeByRank(z,100,-1);
        }
        private static String lb(String g,String f){return "lb:"+g.replace(' ','_')+":"+f;}

        /*────────────────── MATCH STORAGE ──────────────────*/
        void saveMatch(org.albi.core.modules.Duels.ranked.RankedMatchRecord record) {
            String key = "match:" + record.getMatchId();
            String json = G.toJson(record);

            /* 1️⃣ Redis */
            redis.withJedis(j -> {
                j.setex(key, (int) matchTtlSeconds, json);
                return null;
            });

            /* 2️⃣ SQL */
            try (Connection c = sql.getConnection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO ranked_matches(uuid, player1, player2, data) VALUES(?, ?, ?, ?)")) {
                ps.setString(1, record.getMatchId());
                ps.setString(2, record.getPlayer1().toString());
                ps.setString(3, record.getPlayer2().toString());
                ps.setString(4, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
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
    }

    /*──────────────────────────────────────── RedisWrapper ────────────────*/


    public static final class RedisWrapper {
        private final Plugin plugin;
        private final String host;
        private final int port;
        private final String password;
        private JedisPool pool;
        private final JedisPoolConfig cfg = new JedisPoolConfig();

        private volatile boolean healthy = false;
        private volatile int retryAttempts = 0;
        private volatile long lastAttemptTime = 0;

        RedisWrapper(Plugin plugin, String host, int port, String pwd) {
            this.plugin = plugin;
            this.host = host;
            this.port = port;
            this.password = pwd;

            cfg.setMaxTotal(16);
            cfg.setMinIdle(2);
            cfg.setTestOnBorrow(false);
            cfg.setTestOnReturn(false);
            cfg.setTestWhileIdle(true);
            cfg.setMaxWait(Duration.ofSeconds(10));
            cfg.setMinEvictableIdleTime(Duration.ofMinutes(5));
            cfg.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
            cfg.setNumTestsPerEvictionRun(3);

            init(); // first attempt
        }

        private void init() {
            close();

            try {
                boolean sslEnabled = plugin.getConfig().getBoolean("redis.ssl");
                if (sslEnabled) {
                    SSLContext sslContext = getSSLContext();
                    SSLParameters sslParameters = new SSLParameters();
                    sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                    pool = new JedisPool(cfg, host, port, 8000, password, true,
                            sslContext.getSocketFactory(), sslParameters, null);
                } else {
                    pool = new JedisPool(cfg, host, port, 8000, password);
                }

                try (Jedis j = pool.getResource()) {
                    if (!"PONG".equalsIgnoreCase(j.ping())) throw new RuntimeException("No PONG reply");
                }

                healthy = true;
                retryAttempts = 0;
                plugin.getLogger().info("[Stats] Redis pool ready.");
            } catch (Exception e) {
                healthy = false;
                retryAttempts++;
                plugin.getLogger().warning("[Stats] Redis connection attempt " + retryAttempts + " failed ("
                        + e.getClass().getSimpleName() + "). Check the endpoint, authentication and TLS settings.");

                if (retryAttempts <= 3) {
                    // Retry in 5 seconds
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::init, 20 * 5);
                } else {
                    plugin.getLogger().severe("[Stats] Redis disabled after 3 failed attempts.");
                }
            }

            lastAttemptTime = System.currentTimeMillis();
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

        private void ensure() {
            if (healthy) return;

            long now = System.currentTimeMillis();
            if (now - lastAttemptTime >= 5000 && retryAttempts < 3) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::init);
                lastAttemptTime = now;
            }
        }

        public <R> R withJedis(Function<Jedis, R> fn) {
            ensure();
            if (!healthy) return null;

            try (Jedis j = pool.getResource()) {
                return fn.apply(j);
            } catch (Exception e) {
                plugin.getLogger().warning("[Stats] Redis operation failed: " + e.getMessage());
                healthy = false;
                return null;
            }
        }

        public void close() {
            if (pool != null && !pool.isClosed()) {
                pool.close();
            }
        }

        public boolean isHealthy() {
            return healthy;
        }
    }
}
