package org.albi.core.data;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import org.albi.core.data.StatManagement.Field;
import org.albi.core.data.StatManagement.LBRow;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Redis is a bounded, rebuildable index. MySQL holds the full scores used to rebuild it. */
final class Leaderboards {
    static final int LIMIT = 100;
    static final String NAMES = "lb:names";
    private static final String INDEX_SCORE = """
            local previous = redis.call('ZSCORE', KEYS[1], ARGV[2])
            if previous and tonumber(previous) == tonumber(ARGV[1]) then return 0 end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -101)
            if previous and tonumber(previous) > tonumber(ARGV[1]) then redis.call('DEL', KEYS[3]) end
            redis.call('INCR', KEYS[2])
            return 1
            """;
    private static final String REBUILD = """
            if (redis.call('GET', KEYS[2]) or '0') ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            for i = 2, #ARGV, 2 do redis.call('ZADD', KEYS[1], ARGV[i], ARGV[i+1]) end
            redis.call('SET', KEYS[3], '1', 'EX', 60)
            redis.call('INCR', KEYS[2])
            return 1
            """;
    private static final Gson JSON = new Gson();

    // The new namespace forces a rebuild of every board affected by the old trim.
    record Board(String key, String path, boolean ranked) {
        static Board game(String game, Field field) {
            return new Board("lb:v2:game:" + field.redis + ":" + game,
                    "$." + JSON.toJson(game) + "." + field.json, false);
        }

        static Board overall() {
            return new Board("lb:v2:ranked:elo", "$.overallRankedStats.elo", true);
        }

        String revision() { return "lb:v2:revision:" + key; }
        String ready() { return "lb:v2:ready:" + key; }
    }

    private record Index(String revision, List<LBRow> rows) {}

    private final DataSource sql;
    private final StatManagement.RedisWrapper redis;
    private final Logger logger;
    private final boolean storedNames;
    private final Cache<Board, List<LBRow>> lastGood = CacheBuilder.newBuilder().maximumSize(512).build();
    private final Cache<Board, List<LBRow>> recent = CacheBuilder.newBuilder().maximumSize(512)
            .expireAfterWrite(10, TimeUnit.SECONDS).build();

    Leaderboards(DataSource sql, StatManagement.RedisWrapper redis, Logger logger, boolean storedNames) {
        this.sql = sql;
        this.redis = redis;
        this.logger = logger;
        this.storedNames = storedNames;
    }

    List<LBRow> top(Board board, int count) {
        if (count <= 0) return List.of();
        List<LBRow> rows;
        try {
            rows = recent.get(board, () -> refresh(board));
        } catch (ExecutionException failure) {
            rows = cached(board);
            logger.warning("[Stats] Could not refresh leaderboard " + board.key());
        }
        return rows.subList(0, Math.min(count, rows.size()));
    }

    private List<LBRow> refresh(Board board) {
        Index index = redis.withJedis(j -> {
            String revision = Objects.toString(j.get(board.revision()), "0");
            return new Index(revision, j.exists(board.ready()) ? read(j, board) : null);
        });
        if (index != null && index.rows != null) return remember(board, index.rows);

        try {
            List<LBRow> rows = readSql(board);
            if (index != null) {
                redis.withJedis(j -> {
                    if (rebuild(j, board, index.revision, rows)) {
                        try (Pipeline p = j.pipelined()) {
                            for (LBRow row : rows) {
                                if (row.name() != null) p.hsetnx(NAMES, row.uuid().toString(), row.name());
                            }
                            p.sync();
                        }
                    }
                    return null;
                });
            }
            return remember(board, rows);
        } catch (SQLException failure) {
            logger.warning("[Stats] Could not read MySQL leaderboard " + board.key() + ". Serving the last cached result.");
            return cached(board);
        }
    }

    private List<LBRow> remember(Board board, List<LBRow> rows) {
        List<LBRow> immutable = List.copyOf(rows);
        lastGood.put(board, immutable);
        return immutable;
    }

    private List<LBRow> cached(Board board) {
        return Objects.requireNonNullElse(lastGood.getIfPresent(board), List.of());
    }

    private List<LBRow> readSql(Board board) throws SQLException {
        // Only the column name is interpolated. JSON paths are bound parameters even for custom game names.
        String column = board.ranked ? "main" : "stats";
        String name = storedNames ? "player_name" : "NULL";
        String query = "SELECT uuid, " + name + ", CAST(JSON_UNQUOTE(JSON_EXTRACT(" + column + ", ?)) AS SIGNED) AS score "
                + "FROM player_stats WHERE JSON_CONTAINS_PATH(" + column + ", 'one', ?) "
                + "ORDER BY score DESC, uuid DESC LIMIT ?";
        try (var connection = sql.getConnection(); var statement = connection.prepareStatement(query)) {
            statement.setQueryTimeout(5);
            statement.setString(1, board.path);
            statement.setString(2, board.path);
            statement.setInt(3, LIMIT);
            try (var result = statement.executeQuery()) {
                List<LBRow> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(new LBRow(UUID.fromString(result.getString(1)), result.getLong(3), result.getString(2)));
                }
                return rows;
            }
        }
    }

    static List<LBRow> read(Jedis redis, Board board) {
        var scores = redis.zrevrangeWithScores(board.key, 0, LIMIT - 1);
        if (scores.isEmpty()) return List.of();
        var names = redis.hmget(NAMES, scores.stream().map(tuple -> tuple.getElement()).toArray(String[]::new));
        var rows = new ArrayList<LBRow>(scores.size());
        int index = 0;
        for (var score : scores) {
            rows.add(new LBRow(UUID.fromString(score.getElement()), (long) score.getScore(), names.get(index++)));
        }
        return rows;
    }

    static void index(Pipeline pipeline, Board board, UUID player, long score) {
        pipeline.eval(INDEX_SCORE, List.of(board.key, board.revision(), board.ready()), List.of(Long.toString(score), player.toString()));
    }

    static boolean rebuild(Jedis redis, Board board, String revision, List<LBRow> rows) {
        var arguments = new ArrayList<String>(1 + rows.size() * 2);
        arguments.add(revision);
        for (LBRow row : rows) {
            arguments.add(Long.toString(row.score()));
            arguments.add(row.uuid().toString());
        }
        return Long.valueOf(1).equals(redis.eval(REBUILD, List.of(board.key, board.revision(), board.ready()), arguments));
    }
}
