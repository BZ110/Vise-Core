package org.albi.core.data;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceWorkerTest {
    @Test
    void waitsForTheQuitWriteBeforeRunningTheFinalFlush() {
        var writer = new PersistenceWorker();
        var order = new ArrayList<String>();
        assertTrue(writer.submit(() -> order.add("quit save")));

        assertTrue(writer.finish(() -> order.add("final flush"), Duration.ofSeconds(3)));
        assertEquals(List.of("quit save", "final flush"), order);
        assertFalse(writer.submit(() -> fail("Writes must be rejected after shutdown")));
    }

    @Test
    void interruptsAnOutstandingWriteWhenTheShutdownBudgetExpires() throws InterruptedException {
        var writer = new PersistenceWorker();
        var running = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        writer.submit(() -> {
            running.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
                interrupted.countDown();
            }
        });

        assertTrue(running.await(3, TimeUnit.SECONDS));
        assertFalse(writer.finish(() -> {}, Duration.ofMillis(100)));
        assertTrue(interrupted.await(3, TimeUnit.SECONDS));
    }

    @Test
    void savingAnEarlierProfileSnapshotLeavesLaterChangesDirty() {
        var profile = new MainPlayer(java.util.UUID.randomUUID());
        long snapshot = profile.revision();
        profile.setVisibleStats(false);

        profile.markPersisted(snapshot);
        assertTrue(profile.isDirty());
        profile.markPersisted(profile.revision());
        assertFalse(profile.isDirty());
    }
}
