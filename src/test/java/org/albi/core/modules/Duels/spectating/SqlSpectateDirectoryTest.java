package org.albi.core.modules.Duels.spectating;

import org.albi.core.data.SqlNetworkState;
import org.albi.core.testing.TestMySql;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.albi.core.modules.Duels.spectating.SpectateDirectory.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlSpectateDirectoryTest {
    @Test
    void permissionAndOneTimeArrivalSurviveSeparateDatabaseSessions() throws Exception {
        var database = TestMySql.database();
        var namespace = UUID.randomUUID().toString();
        var origin = new SqlSpectateDirectory(new SqlNetworkState(database), namespace, "origin");
        var host = new SqlSpectateDirectory(new SqlNetworkState(database), namespace, "host");
        var other = new SqlSpectateDirectory(new SqlNetworkState(database), namespace, "other");
        origin.prepare();
        var route = new Route(UUID.randomUUID(), "Fighter", UUID.randomUUID(), "host");
        var intent = new Intent(UUID.randomUUID(), "one", "fighter");
        host.exchange((wire, now) -> { wire.publish(Map.of("Fighter", route)); return null; });
        var opened = origin.exchange((wire, now) -> wire.open(intent)).request();
        assertEquals(route, opened.target());
        other.exchange((wire, now) -> { wire.decide(opened, "ready"); return null; });
        assertEquals("pending", origin.exchange((wire, now) -> wire.find(intent.viewer())).state());
        host.exchange((wire, now) -> { wire.decide(opened, "ready"); return null; });
        var ready = origin.exchange((wire, now) -> wire.find(intent.viewer()));
        assertEquals("ready", ready.state());
        assertEquals(Boolean.FALSE, other.exchange((wire, now) -> wire.depart(ready)));
        assertEquals(Boolean.TRUE, origin.exchange((wire, now) -> wire.depart(ready)));
        assertNull(other.exchange((wire, now) -> wire.take(intent.viewer())));
        assertEquals("sent", host.exchange((wire, now) -> wire.take(intent.viewer())).state());
        assertNull(host.exchange((wire, now) -> wire.take(intent.viewer())));
        assertEquals("busy", origin.exchange((wire, now) -> wire.open(intent)).error());
        assertEquals("pending", origin.exchange((wire, now) -> wire.open(new Intent(intent.viewer(), "two", "fighter"))).request().state());
    }

    @Test
    void routeChangeInvalidatesReadyTransferAndStaleCleanupKeepsReplacement() throws Exception {
        var data = new SqlNetworkState(TestMySql.database());
        var scope = UUID.randomUUID().toString();
        var origin = new SqlSpectateDirectory(data, scope, "origin");
        var host = new SqlSpectateDirectory(data, scope, "host");
        host.prepare();
        var route = new Route(UUID.randomUUID(), "Fighter", UUID.randomUUID(), "host");
        var intent = new Intent(UUID.randomUUID(), "one", "Fighter");
        var replacement = new Route(route.player(), route.name(), UUID.randomUUID(), route.server());
        host.exchange((wire, now) -> { wire.publish(Map.of("Fighter", route)); return null; });
        var request = origin.exchange((wire, now) -> wire.open(intent)).request();
        host.exchange((wire, now) -> {
            wire.decide(request, "ready");
            wire.forget(route);
            wire.publish(Map.of("Fighter", replacement));
            wire.forget(route);
            return null;
        });
        var ready = origin.exchange((wire, now) -> wire.find(intent.viewer()));
        assertEquals(Boolean.FALSE, origin.exchange((wire, now) -> wire.depart(ready)));
        var next = origin.exchange((wire, now) -> wire.open(new Intent(UUID.randomUUID(), "next", "Fighter"))).request();
        assertEquals(replacement, next.target());
    }
}
