package org.albi.core.modules.Duels.spectating;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Short-lived match routes and one-use spectator transfers. Bukkit state stays on the host. */
final class SpectateDirectory {
    static final int ROUTE_MS = 6_000;
    static final int REQUEST_MS = 18_000;
    static final int DEPART_GRACE_MS = 5_000;
    private static final Gson JSON = new Gson();

    record Route(UUID player, String name, UUID duel, String server) {}
    record Request(UUID viewer, String token, Route target, String origin, String state) {}
    record Intent(UUID viewer, String token, String name) {}

    private static final String TRANSITION = """
            local op, token, server = ARGV[1], ARGV[2], ARGV[3]
            local function now()
                local clock = redis.call('TIME')
                return tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
            end
            if op == 'pending' then
                redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now())
                local requests = {}
                for _, viewer in ipairs(redis.call('ZRANGE', KEYS[1], 0, 63)) do
                    local request = redis.call('GET', KEYS[2] .. viewer)
                    if request and cjson.decode(request).state == 'pending' then
                        table.insert(requests, request)
                    end
                end
                return '[' .. table.concat(requests, ',') .. ']'
            end
            local raw = redis.call('GET', KEYS[1])
            if op == 'open' then
                if raw then
                    local active = cjson.decode(raw)
                    return active.token == token and raw or 'busy'
                end
                local route = redis.call('GET', KEYS[2])
                if not route then return 'missing' end
                local target = cjson.decode(route)
                if target.server == server then return 'missing' end
                local request = cjson.encode({viewer=ARGV[4], token=token, target=target, origin=server, state='pending'})
                local inbox = KEYS[3] .. target.server
                local ttl = tonumber(ARGV[5])
                redis.call('PSETEX', KEYS[1], ttl, request)
                redis.call('ZADD', inbox, now() + ttl, ARGV[4])
                redis.call('PEXPIRE', inbox, ttl * 2)
                return request
            end
            if op == 'forget' then
                if raw == ARGV[2] then return redis.call('DEL', KEYS[1]) end
                return 0
            end
            if not raw then return 0 end
            local request = cjson.decode(raw)
            if op == 'take' then
                if request.target.server ~= server or request.state ~= 'sent' then return 0 end
                redis.call('DEL', KEYS[1])
                return raw
            end
            if request.token ~= token then return 0 end
            if op == 'cancel' and request.origin == server then
                redis.call('ZREM', KEYS[2] .. request.target.server, request.viewer)
                return redis.call('DEL', KEYS[1])
            end
            if op == 'depart' and request.origin == server and request.state == 'ready'
                    and redis.call('PTTL', KEYS[1]) >= tonumber(ARGV[4]) then
                local route = redis.call('GET', KEYS[2])
                if not route then return 0 end
                local current = cjson.decode(route)
                if current.duel ~= request.target.duel or current.server ~= request.target.server then return 0 end
                request.state = 'sent'
            elseif op == 'decide' and request.target.server == server and request.state == 'pending' then
                request.state = ARGV[4]
                redis.call('ZREM', KEYS[2], request.viewer)
            else
                return 0
            end
            redis.call('SET', KEYS[1], cjson.encode(request), 'KEEPTTL')
            return 1
            """;

    private final String prefix;
    private final String server;
    private volatile String scriptSha;

    SpectateDirectory(String namespace, String server) {
        prefix = "vise:mm:v1:{" + namespace + "}:watch:";
        this.server = server;
    }

    String open(Jedis redis, Intent intent) {
        return (String) transition(redis, List.of(requestKey(intent.viewer), routeKey(intent.name), prefix + "inbox:"),
                List.of("open", intent.token, server, intent.viewer.toString(), Integer.toString(REQUEST_MS)));
    }

    void publish(Jedis redis, Map<String, Route> routes) {
        if (routes.isEmpty()) return;
        try (var pipeline = redis.pipelined()) {
            routes.forEach((name, route) -> pipeline.psetex(routeKey(name), ROUTE_MS, JSON.toJson(route)));
        }
    }

    void forget(Jedis redis, Route route) {
        transition(redis, List.of(routeKey(route.name)), List.of("forget", JSON.toJson(route), server));
    }

    List<Request> pending(Jedis redis) {
        String reply = (String) transition(redis, List.of(prefix + "inbox:" + server, prefix + "request:"),
                List.of("pending", "", server));
        return List.of(JSON.fromJson(reply, Request[].class));
    }

    Request find(Jedis redis, UUID viewer) {
        String raw = redis.get(requestKey(viewer));
        return raw == null ? null : parse(raw);
    }

    boolean decide(Jedis redis, Request request, String decision) {
        return (long) transition(redis, List.of(requestKey(request.viewer), prefix + "inbox:" + server),
                List.of("decide", request.token, server, decision)) == 1;
    }

    boolean depart(Jedis redis, Request request) {
        return (long) transition(redis, List.of(requestKey(request.viewer), routeKey(request.target.name)),
                List.of("depart", request.token, server, Integer.toString(DEPART_GRACE_MS))) == 1;
    }

    Request take(Jedis redis, UUID viewer) {
        Object raw = transition(redis, List.of(requestKey(viewer)), List.of("take", "", server));
        return raw instanceof String json ? parse(json) : null;
    }

    void cancel(Jedis redis, Intent intent) {
        transition(redis, List.of(requestKey(intent.viewer), prefix + "inbox:"), List.of("cancel", intent.token, server));
    }

    static Request parse(String raw) {
        return JSON.fromJson(raw, Request.class);
    }

    private String requestKey(UUID viewer) {
        return prefix + "request:" + viewer;
    }

    private String routeKey(String player) {
        return prefix + "target:" + player.toLowerCase(Locale.ROOT);
    }

    private Object transition(Jedis redis, List<String> keys, List<String> arguments) {
        // The directory handles command lookups and destination joins on different workers.
        String hash = scriptSha;
        if (hash == null) scriptSha = hash = redis.scriptLoad(TRANSITION);
        try {
            return redis.evalsha(hash, keys, arguments);
        } catch (JedisNoScriptException missing) {
            return redis.eval(TRANSITION, keys, arguments);
        }
    }
}
