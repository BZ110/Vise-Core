package org.albi.core.data;

import com.google.gson.JsonParser;
import org.albi.core.modules.Duels.ranked.RankedMatchRecord;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** Optimistic replay leaves a conflicting write in the outbox instead of replacing newer data. */
final class RecoveryDatabase {
    enum Result { WRITTEN, ALREADY_WRITTEN, CONFLICT }

    private final DataSource sql;
    private volatile boolean receiptsAvailable;

    RecoveryDatabase(DataSource sql) { this.sql = sql; }

    void prepare() throws SQLException {
        try (var connection = sql.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vise_recovery_receipts (
                        operation_id CHAR(36) PRIMARY KEY
                    ) ENGINE=InnoDB
                    """);
            receiptsAvailable = true;
        }
    }

    boolean receiptsAvailable() { return receiptsAvailable; }

    Result apply(RecoveryJournal.Write write, boolean names, boolean replay) throws SQLException {
        if (!RecoveryJournal.valid(write)) return Result.CONFLICT;
        try (var connection = sql.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // A receipt commits in the same transaction and also serializes competing helpers.
                if (replay && receiptsAvailable && !claim(connection, write)) return Result.ALREADY_WRITTEN;
                Result result = write.type().equals("match") ? writeMatch(connection, write) : writeProfile(connection, write, names);
                if (result == Result.CONFLICT) connection.rollback();
                else connection.commit();
                return result;
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

    private boolean claim(Connection connection, RecoveryJournal.Write write) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO vise_recovery_receipts(operation_id) VALUES (?)")) {
            insert.setString(1, write.id().toString());
            insert.executeUpdate();
            return true;
        } catch (SQLException failure) {
            if (failure.getErrorCode() == 1062) return false;
            throw failure;
        }
    }

    private Result writeProfile(Connection connection, RecoveryJournal.Write write, boolean names) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO player_stats(uuid, stats, main) VALUES (?, '{}', '{}')
                ON DUPLICATE KEY UPDATE uuid=uuid
                """)) {
            insert.setString(1, write.subject());
            insert.executeUpdate();
        }

        String type = write.type(); // The caller validates this against the two schema column names.
        String existing;
        try (var query = connection.prepareStatement("SELECT " + type + " FROM player_stats WHERE uuid=? FOR UPDATE")) {
            query.setString(1, write.subject());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Missing locked profile row");
                existing = row.getString(1);
            }
        }
        if (sameJson(existing, write.json())) return Result.ALREADY_WRITTEN;
        String base = write.base() == null ? null : write.base().json();
        if (!sameJson(existing, base == null ? "{}" : base)) return Result.CONFLICT;

        String update = "UPDATE player_stats SET " + type + "=?"
                + (names ? ", player_name=COALESCE(?, player_name)" : "") + " WHERE uuid=?";
        try (var statement = connection.prepareStatement(update)) {
            statement.setString(1, write.json());
            if (names) statement.setString(2, write.name());
            statement.setString(names ? 3 : 2, write.subject());
            statement.executeUpdate();
        }
        return Result.WRITTEN;
    }

    private Result writeMatch(Connection connection, RecoveryJournal.Write write) throws SQLException {
        RankedMatchRecord match = RecoveryJournal.JSON.fromJson(write.json(), RankedMatchRecord.class);
        try (var insert = connection.prepareStatement("INSERT INTO ranked_matches(uuid, player1, player2, data) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, write.subject());
            insert.setString(2, match.getPlayer1().toString());
            insert.setString(3, match.getPlayer2().toString());
            insert.setString(4, write.json());
            insert.executeUpdate();
            return Result.WRITTEN;
        } catch (SQLException failure) {
            if (failure.getErrorCode() != 1062) throw failure;
        }
        try (var query = connection.prepareStatement("SELECT data FROM ranked_matches WHERE uuid=? FOR UPDATE")) {
            query.setString(1, write.subject());
            try (var row = query.executeQuery()) {
                return row.next() && sameJson(row.getString(1), write.json()) ? Result.ALREADY_WRITTEN : Result.CONFLICT;
            }
        }
    }

    private static boolean sameJson(String first, String second) {
        return JsonParser.parseString(first).equals(JsonParser.parseString(second));
    }
}
