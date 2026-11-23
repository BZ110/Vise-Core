package org.albi.core.data;

import org.albi.core.testing.TestMySql;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqlChatBufferTest {
    @Test
    void readersCanSkipHistoryAndReadLiveMessagesInCommitOrder() throws Exception {
        var database = TestMySql.database();
        String scope = UUID.randomUUID().toString();
        var writer = new SqlChatBuffer(database, scope);
        var reader = new SqlChatBuffer(database, scope);
        writer.prepare();
        writer.write("earlier");
        long cursor = reader.head();
        writer.write("live-one");
        reader.write("live-two");
        assertEquals(List.of("live-one", "live-two"), reader.after(cursor).stream().map(SqlChatBuffer.Message::payload).toList());
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(
                "UPDATE vise_chat_buffer SET expires_at=UTC_TIMESTAMP(6) WHERE scope_id=?")) {
            statement.setString(1, scope);
            statement.executeUpdate();
        }
        assertTrue(reader.after(cursor).isEmpty());
        reader.purge();
        assertEquals(3, reader.head());
    }
}
