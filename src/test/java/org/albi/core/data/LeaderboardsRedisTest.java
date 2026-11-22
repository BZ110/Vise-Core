package org.albi.core.data;

import org.albi.core.data.StatManagement.Field;
import org.albi.core.data.StatManagement.LBRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LeaderboardsRedisTest {
    private final Leaderboards.Board board = Leaderboards.Board.game(UUID.randomUUID().toString(), Field.WINS);
    private Jedis redis;

    @BeforeEach
    void connect() {
        String port = System.getenv("VISE_TEST_REDIS_PORT");
        assumeTrue(port != null, "Set VISE_TEST_REDIS_PORT to run Redis integration tests.");
        redis = new Jedis("127.0.0.1", Integer.parseInt(port));
    }

    @AfterEach
    void cleanUp() {
        if (redis == null) return;
        redis.del(board.key(), board.revision(), board.ready());
        redis.close();
    }

    @Test
    void tracksSmallBoardsAndKeepsTheHighestHundredScores() {
        index(player(1), 1);
        assertEquals(List.of(new LBRow(player(1), 1)), Leaderboards.read(redis, board));

        try (var batch = redis.pipelined()) {
            for (int score = 2; score <= 130; score++) Leaderboards.index(batch, board, player(score), score);
            batch.sync();
        }

        var rows = Leaderboards.read(redis, board);
        assertEquals(100, rows.size());
        assertEquals(new LBRow(player(130), 130), rows.getFirst());
        assertEquals(new LBRow(player(31), 31), rows.getLast());
    }

    @Test
    void sqlRebuildDoesNotOverwriteAConcurrentRedisUpdate() {
        var rows = List.of(new LBRow(player(1), 25));
        index(player(2), 40);
        assertFalse(Leaderboards.rebuild(redis, board, "0", rows));
        assertEquals(new LBRow(player(2), 40), Leaderboards.read(redis, board).getFirst());

        assertTrue(Leaderboards.rebuild(redis, board, redis.get(board.revision()), rows));
        assertEquals(rows, Leaderboards.read(redis, board));
        assertTrue(redis.exists(board.ready()));
        index(player(1), 0);
        assertFalse(redis.exists(board.ready()), "A lower score can promote someone previously outside the top 100.");
    }

    private void index(UUID player, int score) {
        try (var batch = redis.pipelined()) {
            Leaderboards.index(batch, board, player, score);
            batch.sync();
        }
    }

    private static UUID player(int id) {
        return new UUID(0, id);
    }
}
