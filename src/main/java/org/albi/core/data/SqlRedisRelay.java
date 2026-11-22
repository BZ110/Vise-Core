package org.albi.core.data;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded, expiring requests for Redis cache publication. No arbitrary commands are accepted. */
final class SqlRedisRelay {
    record Job(String key, String token, String payload) {}

    private final DataSource sql;
    private volatile boolean available;

    SqlRedisRelay(DataSource sql) { this.sql = sql; }

    void prepare() throws SQLException {
        try (var connection = sql.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vise_redis_relay (
                        job_key VARCHAR(255) COLLATE utf8mb4_bin PRIMARY KEY,
                        token CHAR(36) NOT NULL,
                        payload MEDIUMTEXT NOT NULL,
                        expires_at DATETIME(6) NOT NULL,
                        claimed_until DATETIME(6),
                        INDEX idx_vise_relay_expiry (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            available = true;
        }
    }

    boolean available() { return available; }

    void offer(String key, String payload, int ttl) throws SQLException {
        if (!available) return;
        try (var connection = sql.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO vise_redis_relay(job_key, token, payload, expires_at)
                VALUES (?, ?, ?, TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)))
                ON DUPLICATE KEY UPDATE token=?, payload=?, expires_at=TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)), claimed_until=NULL
                """)) {
            String token = UUID.randomUUID().toString();
            statement.setString(1, key);
            statement.setString(2, token);
            statement.setString(3, payload);
            statement.setInt(4, ttl);
            statement.setString(5, token);
            statement.setString(6, payload);
            statement.setInt(7, ttl);
            statement.executeUpdate();
        }
    }

    List<Job> requests() throws SQLException {
        try (var connection = sql.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM vise_redis_relay WHERE expires_at <= UTC_TIMESTAMP(6) LIMIT 256");
            try (var rows = statement.executeQuery("""
                    SELECT job_key, token, payload FROM vise_redis_relay WHERE expires_at > UTC_TIMESTAMP(6)
                    AND (claimed_until IS NULL OR claimed_until < UTC_TIMESTAMP(6)) ORDER BY expires_at LIMIT 32
                    """)) {
                List<Job> jobs = new ArrayList<>();
                while (rows.next()) jobs.add(new Job(rows.getString(1), rows.getString(2), rows.getString(3)));
                return jobs;
            }
        }
    }

    boolean claim(Job job) throws SQLException {
        try (var connection = sql.getConnection(); var statement = connection.prepareStatement("""
                UPDATE vise_redis_relay SET claimed_until=TIMESTAMPADD(SECOND, 15, UTC_TIMESTAMP(6))
                WHERE job_key=? AND token=? AND expires_at > UTC_TIMESTAMP(6)
                AND (claimed_until IS NULL OR claimed_until < UTC_TIMESTAMP(6))
                """)) {
            statement.setString(1, job.key());
            statement.setString(2, job.token());
            return statement.executeUpdate() == 1;
        }
    }

    void finish(Job job) throws SQLException {
        try (var connection = sql.getConnection();
             var statement = connection.prepareStatement("DELETE FROM vise_redis_relay WHERE job_key=? AND token=?")) {
            statement.setString(1, job.key());
            statement.setString(2, job.token());
            statement.executeUpdate();
        }
    }
}
