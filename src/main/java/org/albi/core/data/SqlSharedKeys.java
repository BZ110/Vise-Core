package org.albi.core.data;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The experimental MySQL backend. The database clock controls expiry across servers. */
final class SqlSharedKeys {
    private static final String PUT = """
            INSERT INTO vise_shared_keys (key_name, payload, expires_at)
            VALUES (?, ?, TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)))
            ON DUPLICATE KEY UPDATE payload = ?, expires_at = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6))
            """;
    private static final String GET = """
            SELECT payload FROM vise_shared_keys WHERE key_name = ? AND expires_at > UTC_TIMESTAMP(6)
            """;

    private final DataSource database;

    SqlSharedKeys(DataSource database) throws SQLException {
        this.database = database;
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vise_shared_keys (
                        key_name VARCHAR(255) COLLATE utf8mb4_bin PRIMARY KEY,
                        payload MEDIUMTEXT NOT NULL,
                        expires_at DATETIME(6) NOT NULL,
                        INDEX idx_vise_shared_expiry (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    String get(String key) throws SQLException {
        try (var connection = database.getConnection()) {
            return get(connection, key);
        }
    }

    void put(String key, String payload, int ttl) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(PUT)) {
            statement.setString(1, key);
            statement.setString(2, payload);
            statement.setInt(3, ttl);
            statement.setString(4, payload);
            statement.setInt(5, ttl);
            statement.executeUpdate();
        }
    }

    List<String> healthSnapshots() throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT payload FROM vise_shared_keys
                WHERE key_name LIKE 'server:%:health:latest' AND expires_at > UTC_TIMESTAMP(6)
                """); var rows = statement.executeQuery()) {
            List<String> snapshots = new ArrayList<>();
            while (rows.next()) snapshots.add(rows.getString(1));
            return snapshots;
        }
    }

    void purgeExpired() throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                DELETE FROM vise_shared_keys WHERE expires_at <= UTC_TIMESTAMP(6) ORDER BY expires_at LIMIT 1000
                """)) {
            statement.executeUpdate();
        }
    }

    String shareKit(String ownerKey, String json, int ttl, Supplier<String> newCode) throws SQLException {
        try (var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Acquire an exclusive lock on the owner row so two servers reuse the same code.
                lockOwner(connection, ownerKey);
                String code = get(connection, ownerKey);
                LocalDateTime expiresAt = expiry(connection, ttl);
                if (code == null) {
                    for (int attempt = 0; attempt < 8; attempt++) {
                        String candidate = newCode.get();
                        if (reserveCode(connection, SharedState.kitKey(candidate), json, expiresAt)) {
                            code = candidate;
                            break;
                        }
                    }
                } else {
                    put(connection, SharedState.kitKey(code), json, expiresAt);
                }
                if (code != null) put(connection, ownerKey, code, expiresAt);
                connection.commit();
                return code;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private static void lockOwner(Connection connection, String key) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO vise_shared_keys (key_name, payload, expires_at) VALUES (?, '', UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE key_name = key_name
                """)) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    private static LocalDateTime expiry(Connection connection, int ttl) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6))")) {
            statement.setInt(1, ttl);
            try (var row = statement.executeQuery()) {
                row.next();
                return row.getObject(1, LocalDateTime.class);
            }
        }
    }

    private static boolean reserveCode(Connection connection, String key, String payload, LocalDateTime expiresAt) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO vise_shared_keys (key_name, payload, expires_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setString(1, key);
            statement.setString(2, payload);
            statement.setObject(3, expiresAt);
            statement.executeUpdate();
            return true;
        } catch (SQLException failure) {
            if (failure.getErrorCode() != 1062) throw failure; // MySQL duplicate primary key.
            return false;
        }
    }

    private static String get(Connection connection, String key) throws SQLException {
        try (var statement = connection.prepareStatement(GET)) {
            statement.setString(1, key);
            try (var row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private static void put(Connection connection, String key, String payload, LocalDateTime expiresAt) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO vise_shared_keys (key_name, payload, expires_at) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE payload = ?, expires_at = ?
                """)) {
            statement.setString(1, key);
            statement.setString(2, payload);
            statement.setObject(3, expiresAt);
            statement.setString(4, payload);
            statement.setObject(5, expiresAt);
            statement.executeUpdate();
        }
    }
}
