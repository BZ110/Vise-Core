package org.albi.core.data;

import com.google.gson.Gson;
import org.bukkit.configuration.ConfigurationSection;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Separate short transactions for each fleet and coordination feature. */
public final class SqlNetworkState {
    private static final Gson JSON = new Gson();
    private final DataSource database;

    public SqlNetworkState(DataSource database) { this.database = database; }

    public static boolean enabled(ConfigurationSection config, String feature) {
        String key = "network.mysql_failover.";
        return config.getBoolean(key + "enabled", false) && config.getBoolean(key + feature, true);
    }

    public void prepare(String key) throws SQLException {
        try (var connection = database.getConnection(); var ddl = connection.createStatement()) {
            ddl.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vise_network_state (
                      scope_id VARCHAR(160) COLLATE utf8mb4_bin PRIMARY KEY, payload JSON NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            try (var insert = connection.prepareStatement("INSERT IGNORE INTO vise_network_state VALUES (?, '{}')")) {
                insert.setString(1, key);
                insert.executeUpdate();
            }
        }
    }

    public <S, R> R transact(String key, Class<S> type, Supplier<S> empty, BiFunction<S, Long, R> work) throws SQLException {
        try (var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String previous;
                long now;
                try (var select = connection.prepareStatement("""
                        SELECT payload, CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)
                        FROM vise_network_state WHERE scope_id=? FOR UPDATE NOWAIT
                        """)) {
                    select.setString(1, key);
                    select.setQueryTimeout(2);
                    try (var row = select.executeQuery()) {
                        if (!row.next()) throw new SQLException("Missing network coordination row", "42S02");
                        previous = row.getString(1);
                        now = row.getLong(2);
                    }
                }
                S state = previous.equals("{}") ? empty.get() : JSON.fromJson(previous, type);
                String original = JSON.toJson(state);
                R result = work.apply(state, now);
                String updated = JSON.toJson(state);
                if (!updated.equals(original)) {
                    try (var update = connection.prepareStatement("UPDATE vise_network_state SET payload=? WHERE scope_id=?")) {
                        update.setString(1, updated);
                        update.setString(2, key);
                        update.setQueryTimeout(2);
                        update.executeUpdate();
                    }
                }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    public static boolean busy(SQLException error) { return error.getErrorCode() == 3572 || error.getErrorCode() == 1213; }
}
