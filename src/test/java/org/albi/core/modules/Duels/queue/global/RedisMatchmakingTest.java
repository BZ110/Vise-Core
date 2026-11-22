package org.albi.core.modules.Duels.queue.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.albi.core.modules.Duels.queue.global.MatchmakingPolicy.PERSONAL;
import static org.albi.core.modules.Duels.queue.global.RedisMatchmaking.Change;
import static org.albi.core.modules.Duels.queue.global.RedisMatchmaking.Entry;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedisMatchmakingTest {
    private String namespace;
    private int port;
    private final MatchmakingPolicy policy = new MatchmakingPolicy(MatchmakingPolicyTest.RULES);
    private final MatchmakingPolicy.Host central = new MatchmakingPolicy.Host("central", "host-boot", "eu-central",
            Set.of("Axe", PERSONAL), Set.of(), 10, 50, 3);

    @BeforeEach
    void connectToConfiguredTestRedis() {
        String configuredPort = System.getenv("VISE_TEST_REDIS_PORT");
        assumeTrue(configuredPort != null, "Set VISE_TEST_REDIS_PORT to run the Redis integration tests.");
        port = Integer.parseInt(configuredPort);
        namespace = "test-" + UUID.randomUUID();
    }

    @Test
    void requiresLiveTicketsAReservedHostAndBothSourcesBeforeStarting() throws Exception {
        var east = backend("east");
        var west = backend("west");
        var host = backend("central");
        Entry first = entry("east", "eu-east", PERSONAL, "{\"first\":true}");
        Entry second = entry("west", "eu-west", PERSONAL, "{\"second\":true}");

        try (Jedis redis = connect()) {
            poll(east, redis, "a", null, List.of(first));
            poll(west, redis, "b", null, List.of(second));
            poll(host, redis, central.session(), central, List.of());

            // Multiple Paper workers race to scan. Both players must receive the same assignment.
            try (var workers = Executors.newFixedThreadPool(4)) {
                var tasks = java.util.stream.IntStream.range(0, 8).mapToObj(i -> workers.submit(() -> {
                    try (Jedis worker = connect()) {
                        backend("scanner-" + i).findMatches(worker, "worker-" + i);
                    }
                })).toList();
                for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            }
            var offered = poll(host, redis, central.session(), central, List.of()).getFirst();
            String id = offered.id();
            assertEquals("offered", offered.state());
            assertEquals("central", offered.host().server());
            assertEquals(id, poll(east, redis, "a", null, List.of(first)).getFirst().id());
            assertEquals(id, poll(west, redis, "b", null, List.of(second)).getFirst().id());
            assertTrue(Set.of(first.custom(), second.custom()).contains(offered.firstKit()));
            assertNotEquals(offered.firstKit(), offered.secondKit());

            assertEquals("offered", poll(east, redis, "a", null, List.of(first),
                    Change.source("vote", id, first.ticket())).getFirst().state());
            assertEquals("offered", poll(host, redis, "other-boot", null, List.of(),
                    Change.host("reserve", id)).getFirst().state());
            assertEquals("reserved", poll(host, redis, central.session(), central, List.of(),
                    Change.host("reserve", id)).getFirst().state());
            assertEquals("reserved", poll(east, redis, "a", null, List.of(first),
                    Change.source("vote", id, first.ticket())).getFirst().state());
            assertEquals("ready", poll(west, redis, "b", null, List.of(second),
                    Change.source("vote", id, second.ticket())).getFirst().state());

            poll(east, redis, "a", null, List.of(first), Change.source("depart", id, first.ticket()));
            assertEquals("ready", poll(host, redis, central.session(), central, List.of(),
                    Change.host("start", id)).getFirst().state());
            poll(west, redis, "b", null, List.of(second), Change.source("depart", id, second.ticket()));
            var started = poll(host, redis, central.session(), central, List.of(), Change.host("start", id)).getFirst();
            assertEquals("started", started.state());
            assertTrue(started.departedFirst() && started.departedSecond());
            assertEquals("started", poll(host, redis, central.session(), central, List.of(),
                    Change.host("start", id)).getFirst().state());
            assertTrue(poll(host, redis, central.session(), central, List.of(), Change.host("finish", id)).isEmpty());
            assertEquals(0, redis.exists(key("ticket:" + first.ticket().player()),
                    key("ticket:" + second.ticket().player()), key("claim:" + first.ticket().player())));
        }
    }

    @Test
    void staleCancellationCannotReleaseANewTicketAndLiveDisconnectReleasesThePair() {
        var east = backend("east");
        var west = backend("west");
        var host = backend("central");
        Entry first = entry("east", "eu-east", "Axe", null);
        Entry second = entry("west", "eu-west", "Axe", null);
        String oldToken = UUID.randomUUID().toString();

        try (Jedis redis = connect()) {
            poll(east, redis, "a", null, List.of(first), new Change("cancel", null, first.ticket().player(), oldToken));
            poll(west, redis, "b", null, List.of(second));
            poll(host, redis, central.session(), central, List.of());
            host.findMatches(redis, central.session());
            var offer = poll(host, redis, central.session(), central, List.of()).getFirst();
            assertEquals("offered", offer.state());
            assertFalse(poll(east, redis, "a", null, List.of(first),
                    new Change("cancel", null, first.ticket().player(), oldToken)).isEmpty());

            assertTrue(poll(east, redis, "a", null, List.of(), Change.source("cancel", null, first.ticket())).isEmpty());
            assertTrue(poll(host, redis, central.session(), central, List.of()).isEmpty());
            assertFalse(redis.exists(key("claim:" + second.ticket().player())));
            assertTrue(redis.exists(key("ticket:" + second.ticket().player())));
        }
    }

    @Test
    void expiredMembershipCannotCreateAnOffer() {
        var east = backend("east");
        var west = backend("west");
        var host = backend("central");
        Entry first = entry("east", "eu-east", "Axe", null);
        Entry second = entry("west", "eu-west", "Axe", null);
        try (Jedis redis = connect()) {
            poll(east, redis, "a", null, List.of(first));
            poll(west, redis, "b", null, List.of(second));
            poll(host, redis, central.session(), central, List.of());
            redis.pexpire(key("ticket:" + first.ticket().player()), 0);
            host.findMatches(redis, central.session());
            assertTrue(poll(host, redis, central.session(), central, List.of()).isEmpty());
        }
    }

    private RedisMatchmaking backend(String server) {
        var settings = new MatchmakingSettings(namespace, server, "eu-central", true,
                10, 128, 12, 12, MatchmakingPolicyTest.RULES);
        return new RedisMatchmaking(settings, policy);
    }

    private Jedis connect() {
        return new Jedis("127.0.0.1", port);
    }

    private String key(String suffix) {
        return "vise:mm:v1:{" + namespace + "}:" + suffix;
    }

    private static Entry entry(String server, String pool, String kit, String custom) {
        return new Entry(new MatchmakingPolicy.Ticket(UUID.randomUUID(), UUID.randomUUID().toString(),
                server, pool, kit, false, System.currentTimeMillis() - 25_000, 30, 0, custom != null), custom);
    }

    private static List<RedisMatchmaking.Assignment> poll(RedisMatchmaking backend, Jedis redis, String session,
            MatchmakingPolicy.Host heartbeat, List<Entry> entries, Change... changes) {
        return backend.exchange(redis, session, heartbeat, entries, List.of(changes));
    }
}
