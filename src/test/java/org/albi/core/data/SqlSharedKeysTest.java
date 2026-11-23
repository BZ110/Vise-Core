package org.albi.core.data;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.albi.core.testing.TestMySql;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SqlSharedKeysTest {
    private MysqlDataSource database;
    private SqlSharedKeys store;

    @BeforeEach
    void connect() throws Exception {
        database = TestMySql.database();
        store = new SqlSharedKeys(database);
    }

    @Test
    void publishesSnapshotsAcrossConnectionsAndHidesExpiredKeysBeforeCleanup() throws Exception {
        String key = "server:test-" + UUID.randomUUID() + ":health:latest";
        String payload = "{\"server\":\"" + key + "\"}";
        var other = new SqlSharedKeys(database);
        store.put(key, payload, 30);
        assertEquals(payload, other.get(key));
        assertTrue(other.healthSnapshots().contains(payload));

        store.put(key, payload, 0);
        assertNull(other.get(key));
        assertFalse(other.healthSnapshots().contains(payload));
        store.purgeExpired();
        try (var connection = database.getConnection();
             var query = connection.prepareStatement("SELECT COUNT(*) FROM vise_shared_keys WHERE key_name=?")) {
            query.setString(1, key);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
    }

    @Test
    void concurrentShareReusesOneCodeAndACollisionDoesNotReplaceAnotherKit() throws Exception {
        String owner = "sharedkits:getcode:" + UUID.randomUUID() + ":1";
        String otherOwner = "sharedkits:getcode:" + UUID.randomUUID() + ":1";
        String firstCode = randomCode();
        String alternate = randomCode();
        var other = new SqlSharedKeys(database);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> store.shareKit(owner, "kit", 30, () -> firstCode));
            var second = executor.submit(() -> other.shareKit(owner, "kit", 30, () -> alternate));
            String published = first.get(5, TimeUnit.SECONDS);
            assertEquals(published, second.get(5, TimeUnit.SECONDS));
            assertEquals("kit", other.get(SharedState.kitKey(published)));

            String otherCode = store.shareKit(otherOwner, "other kit", 30, new java.util.function.Supplier<>() {
                private boolean collided;

                @Override
                public String get() {
                    if (collided) return published.equals(firstCode) ? alternate : firstCode;
                    collided = true;
                    return published;
                }
            });
            assertNotEquals(published, otherCode);
            assertEquals("kit", other.get(SharedState.kitKey(published)));
            assertEquals("other kit", other.get(SharedState.kitKey(otherCode)));
        }
    }

    @Test
    void redisPeerCanReplayAndAcknowledgeWritesWithoutOverwritingAConflict(@TempDir Path directory) throws Exception {
        String port = System.getenv("VISE_TEST_REDIS_PORT");
        assumeTrue(port != null, "This test requires both MySQL and Redis.");
        createProfileTable();
        var keeper = new RecoveryDatabase(database);
        keeper.prepare();
        var journal = new RecoveryJournal(directory, Logger.getAnonymousLogger());
        UUID player = UUID.randomUUID();
        var first = journal.newWrite("main", player.toString(), null, "{\"xp\":1}", "TestUser");
        var second = journal.newWrite("main", player.toString(), new RecoveryJournal.Profile(first.json()), "{\"xp\":2}", "TestUser");
        var conflict = journal.newWrite("main", player.toString(), null, "{\"xp\":9999}", "TestUser");
        assertTrue(journal.enqueue(first));
        assertTrue(journal.enqueue(second));
        assertEquals(second, new RecoveryJournal(directory, Logger.getAnonymousLogger()).latest(first.key()));

        try (var from = redis(Integer.parseInt(port)); var peer = redis(Integer.parseInt(port))) {
            String namespace = UUID.randomUUID().toString();
            var sender = new RedisRecovery(from, namespace);
            var helper = new RedisRecovery(peer, namespace);
            assertTrue(sender.ask(second));
            assertTrue(sender.ask(first));
            assertEquals(second, helper.latest(first.key()));
            assertEquals(RecoveryDatabase.Result.CONFLICT, keeper.apply(second, true, true));
            assertEquals(java.util.List.of(first, second), helper.pending());
            assertEquals(RecoveryDatabase.Result.WRITTEN, keeper.apply(first, true, true));
            helper.acknowledge(first);
            assertEquals(second, helper.latest(first.key()));
            assertEquals(RecoveryDatabase.Result.WRITTEN, keeper.apply(second, true, true));
            helper.acknowledge(second);
            assertTrue(sender.acknowledged(first));
            assertTrue(sender.acknowledged(second));
            assertTrue(helper.pending().isEmpty());
            assertEquals(RecoveryDatabase.Result.ALREADY_WRITTEN, keeper.apply(first, true, true));
            assertEquals(RecoveryDatabase.Result.CONFLICT, keeper.apply(conflict, true, true));
            try (var connection = database.getConnection();
                 var query = connection.prepareStatement("SELECT JSON_EXTRACT(main, '$.xp') FROM player_stats WHERE uuid=?")) {
                query.setString(1, player.toString());
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                }
            }
        }
    }

    @Test
    void mysqlPeerClaimsOneRedisRequestAndCannotRemoveItsReplacement() throws Exception {
        var source = new SqlRedisRelay(database);
        var peer = new SqlRedisRelay(database);
        source.prepare();
        peer.prepare();
        String key = "health:test-" + UUID.randomUUID();
        source.offer(key, "first", 30);
        var first = peer.requests().stream().filter(job -> job.key().equals(key)).findFirst().orElseThrow();
        assertTrue(peer.claim(first));
        assertFalse(source.claim(first));
        source.offer(key, "second", 30);
        peer.finish(first);
        var second = peer.requests().stream().filter(job -> job.key().equals(key)).findFirst().orElseThrow();
        assertEquals("second", second.payload());
        assertTrue(peer.claim(second));
        peer.finish(second);
        assertTrue(source.requests().stream().noneMatch(job -> job.key().equals(key)));
    }

    @Test
    void mysqlKitCanBeRepublishedAndDiscordIssuanceIsIdempotent() throws Exception {
        String port = System.getenv("VISE_TEST_REDIS_PORT");
        assumeTrue(port != null, "This test requires both MySQL and Redis.");
        try (var redis = redis(Integer.parseInt(port))) {
            var relay = new SqlRedisRelay(database);
            relay.prepare();
            var state = new SharedState(redis, database, false, false, true, true, relay, () -> true, Logger.getAnonymousLogger());
            state.prepareSql();
            UUID player = UUID.randomUUID();
            String code = state.shareKit(player, 1, "{\"name\":\"Mirror\"}");
            assertNotNull(code);
            assertTrue(code.matches("S[0-9A-F]{8}"));
            assertEquals(state.readKit(code), redis.withJedis(j -> j.get(SharedState.kitKey(code))));
            String older = randomCode().substring(1);
            redis.withJedis(j -> j.setex(SharedState.kitKey(older), 60, "legacy"));
            assertTrue(state.copyRelayedRedisKit(older));
            assertEquals("legacy", store.get(SharedState.kitKey(older)));

            UUID request = UUID.randomUUID();
            String link = state.issueDiscordLink(player, request);
            assertNotNull(link);
            assertTrue(link.matches("[0-9]{6}"));
            assertEquals(link, state.issueDiscordLink(player, request));
            assertEquals("pending", state.issueDiscordLink(player, UUID.randomUUID()));
            assertEquals(player.toString(), redis.withJedis(j -> j.get("link-code:" + link)));
            assertTrue(state.issueRelayedDiscordLink(request.toString(), player.toString()));
            assertEquals(link, state.readDiscordLink(request));
        }
    }

    private void createProfileTable() throws Exception {
        try (var connection = database.getConnection(); var ddl = connection.createStatement()) {
            ddl.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                      uuid CHAR(36) PRIMARY KEY, player_name VARCHAR(64), stats JSON NOT NULL, main JSON NOT NULL
                    ) ENGINE=InnoDB
                    """);
        }
    }

    private static StatManagement.RedisWrapper redis(int port) {
        var settings = new YamlConfiguration();
        settings.set("redis.min_idle", 0);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getConfig" -> settings;
                    case "getLogger" -> Logger.getAnonymousLogger();
                    case "isEnabled" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new StatManagement.RedisWrapper(plugin, true, "127.0.0.1", port, null);
    }

    private static String randomCode() {
        return "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
