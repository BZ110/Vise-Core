package org.albi.core.data;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** A short replay window for chat. Polling never replays chat from before this server starts. */
public final class SqlChatBuffer {
    public static final int BATCH_SIZE = 128;
    public record Message(long sequence, String payload) {}

    private final DataSource sql;
    private final String scope;

    public SqlChatBuffer(DataSource sql, String scope) { this.sql = sql; this.scope = scope; }

    public void prepare() throws SQLException {
        try (var connection = sql.getConnection(); var ddl = connection.createStatement()) {
            ddl.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vise_chat_sequence (
                      scope_id VARCHAR(64) COLLATE utf8mb4_bin PRIMARY KEY, sequence_id BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            ddl.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vise_chat_buffer (
                      scope_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, sequence_id BIGINT NOT NULL,
                      payload TEXT NOT NULL, expires_at DATETIME(6) NOT NULL,
                      PRIMARY KEY (scope_id, sequence_id), INDEX idx_vise_chat_expiry (scope_id, expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            try (var insert = connection.prepareStatement("INSERT IGNORE INTO vise_chat_sequence VALUES (?, 0)")) {
                insert.setString(1, scope);
                insert.executeUpdate();
            }
        }
    }

    public long head() throws SQLException {
        try (var connection = sql.getConnection();
             var select = connection.prepareStatement("SELECT sequence_id FROM vise_chat_sequence WHERE scope_id=?")) {
            select.setQueryTimeout(2);
            select.setString(1, scope);
            try (var row = select.executeQuery()) {
                if (!row.next()) throw new SQLException("Missing chat scope", "42S02");
                return row.getLong(1);
            }
        }
    }

    public void write(String payload) throws SQLException {
        try (var connection = sql.getConnection()) {
            connection.setAutoCommit(false);
            try (var select = connection.prepareStatement("SELECT sequence_id FROM vise_chat_sequence WHERE scope_id=? FOR UPDATE NOWAIT");
                 var update = connection.prepareStatement("UPDATE vise_chat_sequence SET sequence_id=? WHERE scope_id=?");
                 var insert = connection.prepareStatement("""
                         INSERT INTO vise_chat_buffer VALUES (?, ?, ?, TIMESTAMPADD(SECOND, 20, UTC_TIMESTAMP(6)))
                         """)) {
                // Issue sequence numbers in commit order so concurrent sends cannot skip a reader's cursor.
                select.setQueryTimeout(2);
                update.setQueryTimeout(2);
                insert.setQueryTimeout(2);
                select.setString(1, scope);
                long sequence;
                try (var row = select.executeQuery()) {
                    if (!row.next()) throw new SQLException("Missing chat scope", "42S02");
                    sequence = row.getLong(1) + 1;
                }
                update.setLong(1, sequence);
                update.setString(2, scope);
                update.executeUpdate();
                insert.setString(1, scope);
                insert.setLong(2, sequence);
                insert.setString(3, payload);
                insert.executeUpdate();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    public List<Message> after(long sequence) throws SQLException {
        try (var connection = sql.getConnection(); var query = connection.prepareStatement("""
                SELECT sequence_id, payload FROM vise_chat_buffer WHERE scope_id=? AND sequence_id>?
                AND expires_at>UTC_TIMESTAMP(6) ORDER BY sequence_id LIMIT ?
                """)) {
            query.setQueryTimeout(2);
            query.setString(1, scope);
            query.setLong(2, sequence);
            query.setInt(3, BATCH_SIZE);
            try (var rows = query.executeQuery()) {
                var result = new ArrayList<Message>();
                while (rows.next()) result.add(new Message(rows.getLong(1), rows.getString(2)));
                return result;
            }
        }
    }

    public void purge() throws SQLException {
        try (var connection = sql.getConnection(); var query = connection.prepareStatement("""
                DELETE FROM vise_chat_buffer WHERE scope_id=? AND expires_at<=UTC_TIMESTAMP(6) LIMIT 4096
                """)) {
            query.setQueryTimeout(2);
            query.setString(1, scope);
            query.executeUpdate();
        }
    }
}
