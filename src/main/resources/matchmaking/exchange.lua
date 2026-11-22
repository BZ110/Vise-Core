-- One script keeps queue membership, assignment ownership and state transitions atomic.
local prefix = KEYS[1]
local request = cjson.decode(ARGV[1])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local waiting = prefix .. 'waiting'
local leases = prefix .. 'ticket-leases'
local servers = prefix .. 'servers'

local function ticketKey(id) return prefix .. 'ticket:' .. id end
local function customKey(id) return prefix .. 'custom:' .. id end
local function claimKey(id) return prefix .. 'claim:' .. id end
local function hostKey(id) return prefix .. 'host:' .. id end
local function matchKey(id) return prefix .. 'match:' .. id end
local function offersKey(server) return prefix .. 'offers:' .. server end

local function removeTicket(id, token)
    local json = redis.call('GET', ticketKey(id))
    if json and cjson.decode(json).token == token then
        redis.call('DEL', ticketKey(id), customKey(id))
        redis.call('ZREM', waiting, id)
        redis.call('ZREM', leases, id)
    end
end

local function getMatch(id)
    local key = matchKey(id)
    local body, state, departedFirst, departedSecond = unpack(redis.call('HMGET', key,
        'body', 'state', 'departed-first', 'departed-second'))
    if not body then return nil end
    local match = cjson.decode(body)
    match.state = state
    match.remaining = redis.call('PTTL', key)
    match.departedFirst = departedFirst == '1'
    match.departedSecond = departedSecond == '1'
    return match
end

local function clearMatch(match, removeTickets)
    for _, ticket in ipairs({match.first, match.second}) do
        if removeTickets then removeTicket(ticket.player, ticket.token) end
        if redis.call('GET', claimKey(ticket.player)) == match.id then
            redis.call('DEL', claimKey(ticket.player))
        end
    end
    redis.call('DEL', matchKey(match.id))
    redis.call('ZREM', offersKey(match.host.server), match.id)
end

local function prune(key)
    redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
end

if request.action == 'offer' then
    local offer = request.offer
    local hostJson = redis.call('GET', hostKey(offer.host.server))
    if not hostJson then return 0 end
    local host = cjson.decode(hostJson)
    local index = offersKey(host.server)
    prune(index)
    local ranked = offer.first.ranked
    local supported = false
    for _, kit in ipairs(ranked and host.ranked or host.casual) do
        if kit == offer.kit then supported = true end
    end
    if host.session ~= offer.host.session or host.arenas <= 0 or not supported
            or redis.call('ZCARD', index) >= request.limit then return 0 end

    local function current(expected)
        local json = redis.call('GET', ticketKey(expected.player))
        if not json or redis.call('EXISTS', claimKey(expected.player)) == 1 then return nil end
        local ticket = cjson.decode(json)
        if ticket.token ~= expected.token or ticket.ranked ~= ranked then return nil end
        -- A refreshed ping or Elo sample invalidates an earlier matchmaking decision.
        if ticket.ping ~= expected.ping or ticket.elo ~= expected.elo or ticket.personal ~= expected.personal then
            return nil
        end
        return ticket
    end

    local first, second = current(offer.first), current(offer.second)
    if not first or not second or first.player == second.player then return 0 end
    if offer.kit == '__PER_CUSTOM__' then
        offer.firstKit = redis.call('GET', customKey(first.player))
        offer.secondKit = redis.call('GET', customKey(second.player))
        if not offer.firstKit or not offer.secondKit then return 0 end
    end

    local lease = request.matchLease
    redis.call('HSET', matchKey(offer.id), 'body', cjson.encode(offer), 'state', 'offered')
    redis.call('PEXPIRE', matchKey(offer.id), lease)
    redis.call('PSETEX', claimKey(first.player), lease, offer.id)
    redis.call('PSETEX', claimKey(second.player), lease, offer.id)
    redis.call('ZADD', index, now + lease, offer.id)
    redis.call('PEXPIRE', index, lease * 2)
    return 1
end

if request.action == 'scan' then
    -- One scanner per interval across the fleet. Rotating through a Redis sample keeps
    -- permanently unmatched tickets from blocking all newer tickets in a large queue.
    if not redis.call('SET', prefix .. 'scanner', request.session, 'NX', 'PX', request.scanLease) then
        return cjson.encode({tickets = {}, hosts = {}, now = now})
    end
    local stale = redis.call('ZRANGEBYSCORE', leases, '-inf', now, 'LIMIT', 0, 512)
    for _, id in ipairs(stale) do
        redis.call('ZREM', waiting, id)
        redis.call('ZREM', leases, id)
    end
    prune(servers)
    local size = redis.call('ZCARD', waiting)
    local anchorCount = math.floor(request.scanSize / 8)
    local randomCount = math.floor(request.scanSize / 4)
    local batch = request.scanSize - anchorCount - randomCount
    local cursor = tonumber(redis.call('GET', prefix .. 'cursor') or '0')
    if cursor >= size then cursor = 0 end
    local ids = redis.call('ZRANGE', waiting, cursor, cursor + batch - 1)
    if #ids < batch and cursor > 0 then
        local wrap = redis.call('ZRANGE', waiting, 0, math.min(cursor, batch - #ids) - 1)
        for _, id in ipairs(wrap) do table.insert(ids, id) end
    end
    for _, id in ipairs(redis.call('ZRANGE', waiting, 0, anchorCount - 1)) do table.insert(ids, id) end
    for _, id in ipairs(redis.call('ZRANDMEMBER', waiting, randomCount)) do table.insert(ids, id) end
    redis.call('PSETEX', prefix .. 'cursor', request.ticketLease * 2, tostring(cursor + batch))
    local tickets, hosts, seen = {}, {}, {}
    for _, id in ipairs(ids) do
        if not seen[id] then
            seen[id] = true
            local raw = redis.call('GET', ticketKey(id))
            if raw and redis.call('EXISTS', claimKey(id)) == 0 then
                table.insert(tickets, cjson.decode(raw))
            end
        end
    end
    for _, id in ipairs(redis.call('ZRANGE', servers, 0, -1)) do
        local raw = redis.call('GET', hostKey(id))
        if raw then table.insert(hosts, cjson.decode(raw)) end
    end
    return cjson.encode({tickets = tickets, hosts = hosts, now = now})
end

local function apply(change)
    if change.action == 'cancel' then
        removeTicket(change.player, change.token)
        local id = redis.call('GET', claimKey(change.player))
        local match = id and getMatch(id)
        if match and match.state ~= 'started' then
            local first = match.first.player == change.player and match.first.token == change.token
            local second = match.second.player == change.player and match.second.token == change.token
            if first or second then clearMatch(match, match.state == 'ready') end
        end
        return
    end

    local match = getMatch(change.match)
    if not match then return end
    local key = matchKey(match.id)
    local host = match.host.session == request.session and match.host.server == request.server
    local heartbeat = redis.call('GET', hostKey(match.host.server))
    local live = heartbeat and cjson.decode(heartbeat).session == match.host.session
    if change.action == 'reserve' and host and match.state == 'offered' then
        redis.call('HSET', key, 'state', 'reserved')
    elseif change.action == 'abort' and host and match.state ~= 'started' then
        clearMatch(match, match.state == 'ready')
    elseif change.action == 'finish' and host and match.state == 'started' then
        clearMatch(match, true)
    elseif change.action == 'start' and host and live and match.state == 'ready' and match.departedFirst and match.departedSecond then
        redis.call('HSET', key, 'state', 'started')
    elseif change.action == 'vote' or change.action == 'depart' then
        local side
        for _, part in ipairs({'first', 'second'}) do
            local ticket = match[part]
            if ticket.player == change.player and ticket.token == change.token and ticket.server == request.server then
                side = part
            end
        end
        if not side or not live then return end
        if change.action == 'vote' and match.state == 'reserved' and match.remaining >= request.minReady then
            redis.call('HSET', key, 'vote-' .. side, '1')
            if redis.call('HEXISTS', key, 'vote-first') == 1 and redis.call('HEXISTS', key, 'vote-second') == 1 then
                redis.call('HSET', key, 'state', 'ready')
            end
        elseif change.action == 'depart' and match.state == 'ready' and match.remaining >= request.minDepart then
            redis.call('HSET', key, 'departed-' .. side, '1')
            removeTicket(change.player, change.token)
        end
    end
end

for _, change in ipairs(request.changes) do apply(change) end

local offerIndex = offersKey(request.server)
prune(offerIndex)
if request.host then
    local key = hostKey(request.server)
    local previous = redis.call('GET', key)
    if not previous or cjson.decode(previous).session == request.session then
        redis.call('PSETEX', key, request.ticketLease, cjson.encode(request.host))
        redis.call('ZADD', servers, now + request.ticketLease, request.server)
        redis.call('PEXPIRE', servers, request.ticketLease * 2)
    end
end

local found = {}
for _, entry in ipairs(request.entries) do
    local ticket = entry.ticket
    local id = redis.call('GET', claimKey(ticket.player))
    if id then
        found[id] = true
    else
        redis.call('PSETEX', ticketKey(ticket.player), request.ticketLease, cjson.encode(ticket))
        if entry.custom then
            redis.call('PSETEX', customKey(ticket.player), request.ticketLease, entry.custom)
        else
            redis.call('DEL', customKey(ticket.player))
        end
        redis.call('ZADD', waiting, ticket.since, ticket.player)
        redis.call('ZADD', leases, now + request.ticketLease, ticket.player)
    end
end
if #request.entries > 0 then
    redis.call('PEXPIRE', waiting, request.ticketLease * 2)
    redis.call('PEXPIRE', leases, request.ticketLease * 2)
end

for _, id in ipairs(redis.call('ZRANGE', offerIndex, 0, -1)) do found[id] = true end
local matches = {}
for id in pairs(found) do
    local match = getMatch(id)
    if match then table.insert(matches, match) end
end
if #matches == 0 then return '[]' end
return cjson.encode(matches)
