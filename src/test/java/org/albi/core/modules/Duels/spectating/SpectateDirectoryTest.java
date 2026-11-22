package org.albi.core.modules.Duels.spectating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.UUID;

import static org.albi.core.modules.Duels.spectating.SpectateDirectory.Intent;
import static org.albi.core.modules.Duels.spectating.SpectateDirectory.Route;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SpectateDirectoryTest {
    private String namespace;
    private int port;

    @BeforeEach
    void configureRedis() {
        String configured = System.getenv("VISE_TEST_REDIS_PORT");
        assumeTrue(configured != null, "Set VISE_TEST_REDIS_PORT to run the Redis integration tests.");
        port = Integer.parseInt(configured);
        namespace = "test-watch-" + UUID.randomUUID();
    }

    @Test
    void transfersOnlyAfterHostApprovalAndConsumesAnArrivalOnce() {
        var origin = directory("east");
        var host = directory("central");
        var other = directory("west");
        var fighter = new Route(UUID.randomUUID(), "Fighter", UUID.randomUUID(), "central");
        var intent = new Intent(UUID.randomUUID(), "first", "fIgHtEr");

        try (Jedis redis = connect()) {
            host.publish(redis, Map.of(fighter.name(), fighter));
            var pending = SpectateDirectory.parse(origin.open(redis, intent));
            assertEquals(pending, SpectateDirectory.parse(origin.open(redis, intent)));
            assertEquals("busy", origin.open(redis, new Intent(intent.viewer(), "second", fighter.name())));
            assertEquals(fighter, pending.target());
            assertTrue(other.pending(redis).isEmpty());
            assertEquals(pending, host.pending(redis).getFirst());
            assertFalse(other.decide(redis, pending, "ready"));
            assertFalse(origin.depart(redis, pending));
            assertNull(host.take(redis, intent.viewer()));

            assertTrue(host.decide(redis, pending, "ready"));
            assertTrue(host.pending(redis).isEmpty());
            assertFalse(other.depart(redis, pending));
            assertTrue(origin.depart(redis, pending));
            assertFalse(origin.depart(redis, pending));
            assertNull(other.take(redis, intent.viewer()));

            var arrived = host.take(redis, intent.viewer());
            assertEquals(fighter.duel(), arrived.target().duel());
            assertEquals("east", arrived.origin());
            assertNull(host.take(redis, intent.viewer()));
        }
    }

    @Test
    void blockedRequestsCannotDepartAndOldCancellationDoesNotDeleteANewIntent() {
        var origin = directory("east");
        var host = directory("central");
        var target = new Route(UUID.randomUUID(), "Fighter", UUID.randomUUID(), "central");
        var previous = new Intent(UUID.randomUUID(), "old", target.name());
        var next = new Intent(previous.viewer(), "new", target.name());

        try (Jedis redis = connect()) {
            host.publish(redis, Map.of(target.name(), target));
            var request = SpectateDirectory.parse(origin.open(redis, previous));
            assertTrue(host.decide(redis, request, "blocked"));
            assertEquals("blocked", origin.find(redis, previous.viewer()).state());
            assertFalse(origin.depart(redis, request));
            assertNull(host.take(redis, previous.viewer()));
            origin.cancel(redis, previous);

            var replacement = SpectateDirectory.parse(origin.open(redis, next));
            origin.cancel(redis, previous);
            assertEquals(replacement, origin.find(redis, next.viewer()));
            origin.cancel(redis, next);
            assertTrue(host.pending(redis).isEmpty());
        }
    }

    @Test
    void staleRouteCleanupCannotEraseANewerMatch() {
        var oldHost = directory("central");
        var newHost = directory("west");
        var origin = directory("east");
        var oldRoute = new Route(UUID.randomUUID(), "Fighter", UUID.randomUUID(), "central");
        var active = new Route(oldRoute.player(), oldRoute.name(), UUID.randomUUID(), "west");

        try (Jedis redis = connect()) {
            oldHost.publish(redis, Map.of(oldRoute.name(), oldRoute));
            newHost.publish(redis, Map.of(active.name(), active));
            oldHost.forget(redis, oldRoute);
            var intent = new Intent(UUID.randomUUID(), "first", active.name());
            assertEquals(active, SpectateDirectory.parse(origin.open(redis, intent)).target());
            newHost.forget(redis, active);
            assertEquals("missing", origin.open(redis, new Intent(UUID.randomUUID(), "second", active.name())));
        }
    }

    @Test
    void admissionStopsIfTheFighterChangesDuelsBeforeDeparture() {
        var host = directory("central");
        var origin = directory("east");
        var fighter = new Route(UUID.randomUUID(), "Fighter", UUID.randomUUID(), "central");
        var intent = new Intent(UUID.randomUUID(), "old-duel", fighter.name());

        try (Jedis redis = connect()) {
            host.publish(redis, Map.of(fighter.name(), fighter));
            var pending = SpectateDirectory.parse(origin.open(redis, intent));
            assertTrue(host.decide(redis, pending, "ready"));

            var nextDuel = new Route(fighter.player(), fighter.name(), UUID.randomUUID(), fighter.server());
            host.publish(redis, Map.of(fighter.name(), nextDuel));
            assertFalse(origin.depart(redis, pending));
            assertNull(host.take(redis, intent.viewer()));
        }
    }

    private SpectateDirectory directory(String server) {
        return new SpectateDirectory(namespace, server);
    }

    private Jedis connect() {
        return new Jedis("127.0.0.1", port);
    }
}
