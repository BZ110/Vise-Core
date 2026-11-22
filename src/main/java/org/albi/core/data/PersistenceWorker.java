package org.albi.core.data;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Owns writes independently of Bukkit's task cancellation during plugin disable. */
final class PersistenceWorker {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ViseCore-persistence");
        thread.setDaemon(true);
        return thread;
    });

    synchronized boolean submit(Runnable task) {
        if (executor.isShutdown()) return false;
        executor.execute(task);
        return true;
    }

    boolean finish(Runnable finalWrite, Duration timeout) {
        synchronized (this) {
            executor.execute(finalWrite);
            executor.shutdown();
        }
        try {
            if (executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        executor.shutdownNow();
        return false;
    }
}
