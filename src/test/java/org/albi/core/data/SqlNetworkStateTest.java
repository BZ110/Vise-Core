package org.albi.core.data;

import org.albi.core.testing.TestMySql;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqlNetworkStateTest {
    private static final class State { int counter; }

    @Test
    void contentionDoesNotSilentlyCommitAndExceptionsRollback() throws Exception {
        var database = TestMySql.database();
        var shared = new SqlNetworkState(database);
        String key = "test:" + UUID.randomUUID();
        shared.prepare(key);
        try (var connection = database.getConnection();
             var lock = connection.prepareStatement("SELECT payload FROM vise_network_state WHERE scope_id=? FOR UPDATE")) {
            connection.setAutoCommit(false);
            lock.setString(1, key);
            try (var rows = lock.executeQuery()) {
                assertTrue(rows.next());
                var error = assertThrows(SQLException.class, () -> shared.transact(key, State.class, State::new,
                        (state, time) -> { throw new AssertionError("contended transaction entered critical section"); }));
                assertTrue(SqlNetworkState.busy(error));
            } finally {
                connection.rollback();
            }
        }
        assertThrows(IllegalStateException.class, () -> shared.transact(key, State.class, State::new, (state, time) -> {
            state.counter++;
            throw new IllegalStateException("test rollback");
        }));
        assertEquals(0, shared.transact(key, State.class, State::new, (state, time) -> state.counter).intValue());
        shared.transact(key, State.class, State::new, (state, time) -> ++state.counter);
        assertEquals(1, shared.transact(key, State.class, State::new, (state, time) -> state.counter).intValue());
    }
}
