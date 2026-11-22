package org.albi.core.modules.Duels.queue.global;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.albi.core.modules.Duels.queue.global.MatchmakingPolicy.*;

class MatchmakingPolicyTest {
    static final long NOW = 100_000;
    static final Rules RULES = new Rules(Map.of(
            "eu-east", new Pool("eu", List.of("eu-central", "eu-west", "na-east"), Map.of("eu-central", 20, "eu-west", 42, "na-east", 105)),
            "eu-central", new Pool("eu", List.of("eu-east", "eu-west", "na-east"), Map.of()),
            "eu-west", new Pool("eu", List.of("eu-central", "eu-east", "na-east"), Map.of("eu-central", 22, "eu-east", 45, "na-east", 85)),
            "na-east", new Pool("na", List.of("eu-west", "eu-central", "eu-east"), Map.of())),
            8, 45, -1, 25, 100, 110, 260, 45, 130, 5, 15);

    private final MatchmakingPolicy policy = new MatchmakingPolicy(RULES);
    private final Host east = host("east", "eu-east", 0, 100);
    private final Host central = host("central", "eu-central", 0, 100);
    private final Host west = host("west", "eu-west", 0, 100);
    private final Host america = host("america", "na-east", 0, 100);

    @Test
    void keepsSamePoolAndChoosesEuropeanMiddleGround() {
        Ticket first = ticket("eu-east", "Axe", false, 20, 25, 0, false);
        Ticket second = ticket("eu-east", "Axe", false, 20, 26, 0, false);
        assertEquals(east, best(first, second, central, east).host());

        Ticket western = ticket("eu-west", "Axe", false, 20, 26, 0, false);
        assertEquals(central, best(first, western, east, west, america, central).host());
    }

    @Test
    void opensPoolPriorityForEachPlayerAndDelaysCrossRegion() {
        Ticket first = ticket("eu-east", "Axe", false, 120, 25, 0, false);
        assertNull(best(first, ticket("eu-west", "Axe", false, 7, 25, 0, false), central));
        assertEquals(central, best(first, ticket("eu-west", "Axe", false, 8, 25, 0, false), central).host());
        assertNull(best(first, ticket("eu-west", "Axe", false, 15, 25, 0, false), east));
        assertNull(best(first, ticket("eu-east", "Axe", false, 44, 25, 0, false), america));
        assertEquals(america, best(first, ticket("eu-east", "Axe", false, 45, 25, 0, false), america).host());
        assertNull(best(first, ticket("asia-central", "Axe", false, 120, 25, 0, false), central));
    }

    @Test
    void widensIndividualPingAndGapWhileKeepingHardCaps() {
        Ticket low = ticket("eu-east", "Axe", false, 0, 25, 0, false);
        Ticket high = ticket("eu-east", "Axe", false, 0, 105, 0, false);
        assertNull(best(low, high, east));
        assertNotNull(best(waiting(low, 15), waiting(high, 15), east));

        Ticket tooHigh = ticket("eu-east", "Axe", false, 200, 261, 0, false);
        assertNull(best(waiting(low, 200), tooHigh, east));
        assertNotNull(policy.best(low, List.of(high), List.of(east), NOW, true));
    }

    @Test
    void preservesRankedEloAndKeepsPersonalKitsOutOfRanked() {
        Ticket first = ticket("eu-east", "Axe", true, 0, 25, 1_000, false);
        Ticket second = ticket("eu-east", "Axe", true, 0, 25, 1_100, false);
        assertNull(best(first, second, east));
        assertNotNull(best(waiting(first, 6), second, east));

        Ticket quickWithKit = ticket("eu-east", QUICK, false, 0, 25, 0, true);
        Ticket quickWithoutKit = ticket("eu-east", QUICK, false, 0, 25, 0, false);
        Ticket personal = ticket("eu-east", PERSONAL, false, 0, 25, 0, true);
        assertNull(best(quickWithKit, first, east));
        assertEquals(PERSONAL, best(quickWithKit, personal, east).kit());
        assertNull(best(quickWithoutKit, personal, east));
        assertEquals("Axe", best(quickWithKit, quickWithoutKit, east).kit());
    }

    @Test
    void avoidsFullAndIncompatibleHosts() {
        Ticket first = ticket("eu-east", "Axe", true, 0, 25, 1_000, false);
        Ticket second = ticket("eu-east", "Axe", true, 0, 25, 1_000, false);
        Host full = host("full", "eu-east", 99, 100);
        Host casualOnly = new Host("casual", "session", "eu-east", Set.of("Axe"), Set.of(), 0, 100, 1);
        assertNull(best(first, second, full, casualOnly));
        assertEquals(east, best(first, second, full, casualOnly, east).host());
    }

    private Match best(Ticket first, Ticket second, Host... hosts) {
        return policy.best(first, List.of(second), List.of(hosts), NOW, false);
    }

    static Ticket ticket(String pool, String kit, boolean ranked, int age, int ping, int elo, boolean personal) {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID().toString(), "origin-" + pool,
                pool, kit, ranked, NOW - age * 1000L, ping, elo, personal);
    }

    private static Ticket waiting(Ticket ticket, int seconds) {
        return new Ticket(ticket.player(), ticket.token(), ticket.server(), ticket.pool(), ticket.kit(), ticket.ranked(),
                NOW - seconds * 1000L, ticket.ping(), ticket.elo(), ticket.personal());
    }

    private static Host host(String id, String pool, int current, int cap) {
        return new Host(id, "session", pool, Set.of("Axe", PERSONAL), Set.of("Axe"), current, cap, 1);
    }
}
