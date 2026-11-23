package org.albi.core.modules.Duels.spectating;

import org.albi.core.data.SqlNetworkState;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.albi.core.modules.Duels.spectating.SpectateDirectory.*;

/** The SQL deployment uses this directory on both healthy and Redis-isolated servers. */
final class SqlSpectateDirectory {
    private static final int MAX_REQUESTS = 1024;
    private static final int MAX_ROUTES = 2048;

    private record LiveRoute(Route route, long expires) {}
    private record LiveRequest(Request request, long expires) {}
    private static final class Directory {
        Map<String, LiveRoute> routes = new HashMap<>();
        Map<UUID, LiveRequest> requests = new HashMap<>();
        Map<String, Long> consumed = new HashMap<>();
    }

    private final SqlNetworkState sql;
    private final String key;
    private final String server;

    SqlSpectateDirectory(SqlNetworkState sql, String namespace, String server) {
        this.sql = sql;
        key = "spectate:" + namespace;
        this.server = server;
    }

    <T> T exchange(BiFunction<Session, Long, T> callback) throws SQLException {
        return sql.transact(key, Directory.class, Directory::new, (state, now) -> {
            state.routes.values().removeIf(route -> route.expires <= now);
            state.requests.values().removeIf(request -> request.expires <= now);
            state.consumed.values().removeIf(expires -> expires <= now);
            return callback.apply(new Session(state, now), now);
        });
    }

    void prepare() throws SQLException { sql.prepare(key); }

    final class Session implements NetworkSpectating.Wire {
        private final Directory state;
        private final long now;

        Session(Directory state, long now) { this.state = state; this.now = now; }

        @Override public void publish(Map<String, Route> routes) {
            routes.forEach((name, route) -> {
                String key = name.toLowerCase(Locale.ROOT);
                LiveRoute existing = state.routes.get(key);
                if (existing != null && !existing.route.equals(route)) return;
                if (existing != null || state.routes.size() < MAX_ROUTES) {
                    state.routes.put(key, new LiveRoute(route, now + ROUTE_MS));
                }
            });
        }

        @Override public void forget(Route route) {
            state.routes.computeIfPresent(route.name().toLowerCase(Locale.ROOT), (name, live) -> live.route.equals(route) ? null : live);
        }

        @Override public NetworkSpectating.Reply open(Intent intent) {
            if (state.consumed.containsKey(intent.token())) return new NetworkSpectating.Reply(intent, null, "busy");
            Request active = find(intent.viewer());
            if (active != null) {
                if (active.token().equals(intent.token()) && active.origin().equals(server)) {
                    return new NetworkSpectating.Reply(intent, active, null);
                }
                return new NetworkSpectating.Reply(intent, null, "busy");
            }
            var route = state.routes.get(intent.name().toLowerCase(Locale.ROOT));
            if (route == null || route.route.server().equals(server)) return new NetworkSpectating.Reply(intent, null, "missing");
            if (state.requests.size() + state.consumed.size() >= MAX_REQUESTS) return new NetworkSpectating.Reply(intent, null, "busy");
            var request = new Request(intent.viewer(), intent.token(), route.route, server, "pending");
            state.requests.put(intent.viewer(), new LiveRequest(request, now + REQUEST_MS));
            return new NetworkSpectating.Reply(intent, request, null);
        }

        @Override public Request find(UUID viewer) {
            var found = state.requests.get(viewer);
            return found == null ? null : found.request;
        }

        @Override public List<Request> pending() {
            return state.requests.values().stream().map(LiveRequest::request)
                    .filter(r -> "pending".equals(r.state()) && server.equals(r.target().server())).limit(64).toList();
        }

        @Override public void decide(Request request, String decision) {
            var current = state.requests.get(request.viewer());
            if (current != null && current.request.equals(request) && "pending".equals(request.state())
                    && server.equals(request.target().server()) && List.of("ready", "blocked", "ended").contains(decision)) {
                transition(current, decision);
            }
        }

        @Override public boolean depart(Request request) {
            var current = state.requests.get(request.viewer());
            if (current == null || !current.request.equals(request) || !"ready".equals(request.state())
                    || !server.equals(request.origin()) || current.expires - now < DEPART_GRACE_MS) return false;
            var route = state.routes.get(request.target().name().toLowerCase(Locale.ROOT));
            if (route == null || !request.target().equals(route.route)) return false;
            transition(current, "sent");
            return true;
        }

        @Override public Request take(UUID viewer) {
            Request active = find(viewer);
            if (active == null || !"sent".equals(active.state()) || !server.equals(active.target().server())) return null;
            // Keep a tombstone until expiry to stop a queued retry from reopening the same transfer.
            state.consumed.put(active.token(), state.requests.remove(viewer).expires);
            return active;
        }

        @Override public void cancel(Intent intent) {
            Request active = find(intent.viewer());
            if (active == null || !active.token().equals(intent.token()) || !server.equals(active.origin())) return;
            state.requests.remove(intent.viewer());
        }

        private void transition(LiveRequest current, String next) {
            var r = current.request;
            state.requests.put(r.viewer(), new LiveRequest(new Request(r.viewer(), r.token(), r.target(), r.origin(), next), current.expires));
        }
    }
}
