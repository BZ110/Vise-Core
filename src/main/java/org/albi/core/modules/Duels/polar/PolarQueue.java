package org.albi.core.modules.Duels.polar;

import org.albi.core.Core;
import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PolarQueue {

    private final Core plugin;
    private final Queue<QueuedTask> queue = new ArrayDeque<>();
    private boolean running = false;

    public PolarQueue(Core plugin) {
        this.plugin = plugin;
    }

    public synchronized void enqueue(String label, Consumer<Runnable> action) {
        queue.add(new QueuedTask(label, action));
        if (!running) {
            running = true;
            runNext();
        }
    }

    private synchronized QueuedTask pollNext() {
        return queue.poll();
    }

    private void runNext() {
        QueuedTask task = pollNext();
        if (task == null) {
            synchronized (this) {
                running = false;
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            AtomicBoolean completed = new AtomicBoolean(false);
            Runnable done = () -> {
                if (!completed.compareAndSet(false, true)) return;
                Bukkit.getScheduler().runTaskLater(plugin, this::runNext, 250L);
            };

            try {
                task.action.accept(done);
            } catch (Throwable throwable) {
                plugin.getLogger().severe("[PolarQueue] Task failed: " + task.label + " -> " + throwable.getMessage());
                throwable.printStackTrace();
                done.run();
            }
        });
    }

    private record QueuedTask(String label, Consumer<Runnable> action) {}
}
