package scheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TiredThread extends Thread implements Comparable<TiredThread> {

    private static final Runnable POISON_PILL = () -> {}; // Special task to signal shutdown

    private final int id; // Worker index assigned by the executor
    private final double fatigueFactor; // Multiplier for fatigue calculation

    private final AtomicBoolean alive = new AtomicBoolean(true); // Indicates if the worker should keep running

    // Single-slot handoff queue; executor will put tasks here
    private final BlockingQueue<Runnable> handoff = new ArrayBlockingQueue<>(1);

    private final AtomicBoolean busy = new AtomicBoolean(false); // Indicates if the worker is currently executing a task

    private final AtomicLong timeUsed = new AtomicLong(0); // Total time spent executing tasks
    private final AtomicLong timeIdle = new AtomicLong(0); // Total time spent idle
    private final AtomicLong idleStartTime = new AtomicLong(0); // Timestamp when the worker became idle

    public TiredThread(int id, double fatigueFactor) {
        this.id = id;
        this.fatigueFactor = fatigueFactor;
        this.idleStartTime.set(System.nanoTime());
        setName(String.format("FF=%.2f", fatigueFactor));
    }

    public int getWorkerId() {
        return id;
    }

    public double getFatigue() {
        return fatigueFactor * timeUsed.get();
    }

    public boolean isBusy() {
        return busy.get();
    }

    public long getTimeUsed() {
        return timeUsed.get();
    }

    public long getTimeIdle() {
        return timeIdle.get();
    }

    /**
     * Assign a task to this worker.
     * This method is non-blocking: if the worker is not ready to accept a task,
     * it throws IllegalStateException.
     */
    public void newTask(Runnable task) {

        java.util.Objects.requireNonNull(task, "task must not be null");

        // Reserve the worker (prevents double-assignment)
        if (!busy.compareAndSet(false, true)) {
            throw new IllegalStateException("Worker is not ready to accept a task (busy)");
        }

        // Verify worker is alive
        if (!alive.get()) {
            busy.set(false);
            throw new IllegalStateException("Worker is shutting down");
        }

        // End the current idle interval (if any) now that we got assigned work
        long now = System.nanoTime();
        long idleStart = idleStartTime.getAndSet(0L); // 0 means not currently idle
        if (idleStart != 0L && idleStart <= now) {
            timeIdle.addAndGet(now - idleStart);
        }

        if (!handoff.offer(task)) {
            // Roll back reservation so executor can try someone else.
            busy.set(false);

            // If we couldn't enqueue, consider ourselves idle again starting now.
            idleStartTime.compareAndSet(0L, now);

            throw new IllegalStateException("Worker is not ready to accept a task (handoff full)");
        }
    }

    /**
     * Request this worker to stop after finishing current task.
     * Inserts a poison pill so the worker wakes up and exits.
     */
    public void shutdown() {
       // TODO
       // If already shutting down, don't try again.
        if (!alive.getAndSet(false)) {
            return;
        }
        try {
            handoff.put(POISON_PILL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Best effort fallback:
            handoff.offer(POISON_PILL);
        }
    }


    @Override
    public void run() {
        while (true) {
            final Runnable task;
            try {
                task = handoff.take(); // blocks until a task (or poison pill) arrives
            } catch (InterruptedException e) {
                // Ignore interrupts and keep running
                continue;
            }

            if (task == POISON_PILL) {
                // Close any ongoing idle interval up to now
                long now = System.nanoTime();
                long idleStart = idleStartTime.getAndSet(0L);
                if (idleStart != 0L && idleStart <= now) {
                    timeIdle.addAndGet(now - idleStart);
                }

                busy.set(false);
                return;
            }

            // Measure only the time spent executing tasks
            long start = System.nanoTime();
            try {
                task.run();
            } catch (Throwable t) {
                // Keep the worker alive even if a task fails
            } finally {
                long end = System.nanoTime();
                timeUsed.addAndGet(end - start);

                // Become idle again.
                busy.set(false);
                idleStartTime.set(end);
            }
        }
    }

    @Override
    public int compareTo(TiredThread o) {
        // TODO
        int c = Double.compare(this.getFatigue(), o.getFatigue());
        if (c != 0) return c;
        return Integer.compare(this.id, o.id);
    }
}