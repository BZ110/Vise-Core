package org.albi.core.modules.Duels.queue.global;

import org.albi.core.data.SqlNetworkState;
import org.albi.core.testing.TestMySql;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.albi.core.modules.Duels.queue.global.MatchmakingPolicy.PERSONAL;
import static org.albi.core.modules.Duels.queue.global.RedisMatchmaking.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlMatchmakingTest {
    @Test
    void sqlFleetSharesOneAssignmentAndRequiresHostAndBothSources() throws Exception {
        var data = new SqlNetworkState(TestMySql.database());
        String scope = "sql-test-" + UUID.randomUUID();
        var east = backend(data, scope, "east");
        var west = backend(data, scope, "west");
        var host = backend(data, scope, "central");
        var central = new MatchmakingPolicy.Host("central", "host-boot", "eu-central", Set.of(PERSONAL), Set.of(), 1, 30, 3);
        var first = ticket("east", "eu-east", "first");
        var second = ticket("west", "eu-west", "second");
        east.prepare();
        east.exchange("a", null, List.of(first), List.of());
        west.exchange("b", null, List.of(second), List.of());
        host.exchange(central.session(), central, List.of(), List.of());

        List<Assignment> assignments = List.of();
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (assignments.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(60);
            assignments = host.exchange(central.session(), central, List.of(), List.of());
        }
        Assignment offered = assignments.getFirst();
        String id = offered.id();
        assertEquals("offered", offered.state());
        assertEquals(Set.of(first.custom(), second.custom()), Set.of(offered.firstKit(), offered.secondKit()));
        assertEquals(id, east.exchange("a", null, List.of(first), List.of()).getFirst().id());
        assertEquals(id, west.exchange("b", null, List.of(second), List.of()).getFirst().id());
        assertEquals("offered", east.exchange("a", null, List.of(first),
                List.of(Change.host("reserve", id), Change.source("vote", id, second.ticket()))).getFirst().state());
        assertEquals("reserved", host.exchange(central.session(), central, List.of(), List.of(Change.host("reserve", id))).getFirst().state());
        assertEquals("reserved", east.exchange("a", null, List.of(first), List.of(Change.source("vote", id, first.ticket()))).getFirst().state());
        assertEquals("ready", west.exchange("b", null, List.of(second), List.of(Change.source("vote", id, second.ticket()))).getFirst().state());
        east.exchange("a", null, List.of(first), List.of(Change.source("depart", id, first.ticket())));
        assertEquals("ready", host.exchange(central.session(), central, List.of(), List.of(Change.host("start", id))).getFirst().state());
        west.exchange("b", null, List.of(second), List.of(Change.source("depart", id, second.ticket())));
        assertEquals("started", host.exchange(central.session(), central, List.of(), List.of(Change.host("start", id))).getFirst().state());
        assertTrue(host.exchange(central.session(), central, List.of(), List.of(Change.host("finish", id))).isEmpty());
    }

    private static Entry ticket(String server, String pool, String custom) {
        return new Entry(new MatchmakingPolicy.Ticket(UUID.randomUUID(), UUID.randomUUID().toString(), server, pool, PERSONAL,
                false, System.currentTimeMillis() - 120_000, 40, 1000, true), custom);
    }

    private static SqlMatchmaking backend(SqlNetworkState data, String namespace, String server) {
        var rules = MatchmakingPolicyTest.RULES;
        var settings = new MatchmakingSettings(namespace, server, "eu-central", true, 10, 32, 8, 4, rules);
        return new SqlMatchmaking(data, settings, new MatchmakingPolicy(rules));
    }
}
