package org.albi.core.modules.Duels.queue.global;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class MatchmakingPolicy {
    static final String QUICK = "__QUICK__";
    static final String PERSONAL = "__PER_CUSTOM__";

    record Ticket(UUID player, String token, String server, String pool, String kit, boolean ranked,
                  long since, int ping, int elo, boolean personal) {}

    record Host(String server, String session, String pool, Set<String> casual, Set<String> ranked,
                int players, int capacity, int arenas) {}

    record Pool(String region, List<String> priority, Map<String, Integer> latency, Map<String, Integer> steps) {
        Pool(String region, List<String> priority, Map<String, Integer> latency) {
            this(region, priority, latency, Map.of());
        }
    }

    record Rules(Map<String, Pool> pools, int priorityStep, int crossRegionAfter, int unlistedAfter,
                 int sameRegionLatency, int crossRegionLatency, int pingStart, int pingMax,
                 int gapStart, int gapMax, int relaxEvery, int relaxBy) {}

    record Match(Ticket first, Ticket second, Host host, String kit) {}

    record Score(int foreign, int priority, int totalPriority, int worstPing, int gap, int sum,
                 int eloGap, int load, long opponentSince) implements Comparable<Score> {
        private static final Comparator<Score> ORDER = Comparator.comparingInt(Score::foreign)
                .thenComparingInt(Score::priority).thenComparingInt(Score::totalPriority)
                .thenComparingInt(Score::worstPing).thenComparingInt(Score::gap).thenComparingInt(Score::sum)
                .thenComparingInt(Score::eloGap).thenComparingInt(Score::load).thenComparingLong(Score::opponentSince);

        @Override
        public int compareTo(Score other) {
            return ORDER.compare(this, other);
        }
    }

    private final Rules rules;

    MatchmakingPolicy(Rules rules) {
        this.rules = rules;
    }

    Match best(Ticket first, List<Ticket> candidates, List<Host> hosts, long now, boolean local) {
        Match best = null;
        Score bestScore = null;
        int waitFirst = seconds(first, now);
        for (Ticket second : candidates) {
            if (first.player.equals(second.player) || first.ranked != second.ranked) continue;

            String kit = compatibleKit(first, second);
            if (kit == null || (PERSONAL.equals(kit) && !(first.personal && second.personal))) continue;
            int waitSecond = seconds(second, now);
            int eloGap = first.ranked ? Math.abs(first.elo - second.elo) : 0;
            if (eloGap > eloRange(Math.max(waitFirst, waitSecond))) continue;

            for (Host host : hosts) {
                String hostedKit = hostedKit(first, second, host, kit);
                if (hostedKit == null || host.arenas < 1 || (!local && !hasRoom(first, second, host))) continue;

                Score score = local ? new Score(0, 0, 0, 0, 0, 0, eloGap, 0, second.since)
                        : score(first, second, waitFirst, waitSecond, host, eloGap);
                if (score != null && (bestScore == null || score.compareTo(bestScore) < 0)) {
                    best = new Match(first, second, host, hostedKit);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static boolean hasRoom(Ticket first, Ticket second, Host host) {
        int arrivals = (first.server.equals(host.server) ? 0 : 1) + (second.server.equals(host.server) ? 0 : 1);
        return arrivals <= Math.max(0, host.capacity - host.players);
    }

    private static String compatibleKit(Ticket first, Ticket second) {
        if (first.kit.equals(second.kit)) return first.kit;
        if (first.ranked) return null;
        if (QUICK.equals(first.kit)) return second.kit;
        if (QUICK.equals(second.kit)) return first.kit;
        return null;
    }

    private static String hostedKit(Ticket first, Ticket second, Host host, String kit) {
        Set<String> kits = first.ranked ? host.ranked : host.casual;
        if (!QUICK.equals(kit)) return kits.contains(kit) ? kit : null;

        // Pick a stable public kit for this pairing even if a different server runs the scan.
        List<String> standard = kits.stream().filter(k -> !PERSONAL.equals(k)).sorted().toList();
        if (standard.isEmpty()) return null;
        int index = Math.floorMod(first.player.hashCode() ^ second.player.hashCode(), standard.size());
        return standard.get(index);
    }

    private Score score(Ticket first, Ticket second, int waitFirst, int waitSecond, Host host, int eloGap) {
        int priorityFirst = priority(first, host, waitFirst);
        int prioritySecond = priority(second, host, waitSecond);
        if (priorityFirst < 0 || prioritySecond < 0) return null;

        int pingFirst = ping(first, host);
        int pingSecond = ping(second, host);
        int gap = Math.abs(pingFirst - pingSecond);
        if (pingFirst > relax(rules.pingStart, rules.pingMax, waitFirst)
                || pingSecond > relax(rules.pingStart, rules.pingMax, waitSecond)
                || gap > relax(rules.gapStart, rules.gapMax, Math.min(waitFirst, waitSecond))) return null;

        int foreign = (sameRegion(first.pool, host.pool) ? 0 : 1) + (sameRegion(second.pool, host.pool) ? 0 : 1);
        int load = (int) (1000L * host.players / Math.max(1, host.capacity));
        return new Score(foreign, Math.max(priorityFirst, prioritySecond), priorityFirst + prioritySecond,
                Math.max(pingFirst, pingSecond), gap, pingFirst + pingSecond, eloGap, load, second.since);
    }

    private int priority(Ticket ticket, Host host, int wait) {
        String origin = ticket.pool;
        String destination = host.pool;
        if (origin.equals(destination)) {
            if (!unlabeledTransfer(ticket, host)) return 0;
            return wait >= rules.crossRegionAfter ? 1 : -1;
        }
        Pool pool = rules.pools.get(origin);
        int rank = pool == null ? -1 : pool.priority.indexOf(destination);
        if (rank < 0 && rules.unlistedAfter < 0) return -1;
        int priority = rank < 0 ? (pool == null ? 1 : pool.priority.size() + 1)
                : pool.steps.getOrDefault(destination, rank + 1);
        int threshold = rank < 0 ? rules.unlistedAfter : priority * rules.priorityStep;
        if (!sameRegion(origin, destination)) threshold = Math.max(threshold, rules.crossRegionAfter);
        return wait >= threshold ? priority : -1;
    }

    private int ping(Ticket ticket, Host host) {
        if (ticket.pool.equals(host.pool)) {
            return ticket.ping + (unlabeledTransfer(ticket, host) ? rules.crossRegionLatency : 0);
        }
        Pool pool = rules.pools.get(ticket.pool);
        int fallback = sameRegion(ticket.pool, host.pool) ? rules.sameRegionLatency : rules.crossRegionLatency;
        return ticket.ping + (pool == null ? fallback : pool.latency.getOrDefault(host.pool, fallback));
    }

    private boolean unlabeledTransfer(Ticket ticket, Host host) {
        if (!"global".equals(ticket.pool) || ticket.server.equals(host.server)) return false;
        Pool global = rules.pools.get("global");
        return global != null && !global.steps.isEmpty();
    }

    private boolean sameRegion(String first, String second) {
        return region(first).equals(region(second));
    }

    private String region(String id) {
        Pool pool = rules.pools.get(id);
        return pool == null ? id.split("-", 2)[0] : pool.region;
    }

    private int relax(int start, int cap, int wait) {
        return (int) Math.min(cap, start + (long) (wait / rules.relaxEvery) * rules.relaxBy);
    }

    static int eloRange(int seconds) {
        return 50 + (Math.max(0, seconds) / 3) * 25;
    }

    private static int seconds(Ticket ticket, long now) {
        return (int) Math.clamp((now - ticket.since) / 1000, 0, 86_400);
    }
}
