package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);
    
    private boolean accepting = true;


    public TiredExecutor(int numThreads) {
        // TODO
        if (numThreads <= 0){
            throw new IllegalArgumentException("numThreads must be > 0");
        }
        this.workers = new TiredThread[numThreads];

        // Create and start workers, initially all are idle
        for (int i = 0; i < numThreads; i++) {
            double fatigueFactor = 0.5 + Math.random(); // [0.5, 1.5)
            TiredThread w = new TiredThread(i, fatigueFactor);
            workers[i] = w;
            w.start();
            idleMinHeap.add(w);
        }
    }

    public void submit(Runnable task) {
        // TODO
        Objects.requireNonNull(task, "task must not be null");

        while (true) {
            final TiredThread worker;

            // Reserve an idle worker (or wait)
            synchronized (this) {
                if (!accepting) {
                    throw new IllegalStateException("Executor is shut down");
                }

                while (idleMinHeap.isEmpty()) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for an idle worker", e);
                    }
                    if (!accepting) {
                        throw new IllegalStateException("Executor is shut down");
                    }
                }

                worker = idleMinHeap.poll();
                inFlight.incrementAndGet();
            }

            // Wrap task so executor is notified when it completes
            Runnable wrapped = () -> {
                try {
                    task.run();
                } catch (Throwable ignored) {
                    // keep worker alive regardless of task failure
                } finally {
                    cleanUp(worker)
                }
            };

            // Hand off to worker; if it fails (rare race), roll back and retry
            try {
                worker.newTask(wrapped);
                return;
            } catch (IllegalStateException e) {
                synchronized (this) {
                    cleanUp(worker)
                }
                // retry picking a worker
            }
        }
        
    }

    public void submitAll(Iterable<Runnable> tasks) {
        // TODO: submit tasks one by one and wait until all finish
        Objects.requireNonNull(tasks, "tasks must not be null");

        List<Runnable> list = new ArrayList<>();
        for (Runnable t : tasks) {
            Objects.requireNonNull(t, "tasks contains null");
            list.add(t);
        }
        if (list.isEmpty()) {
            return;
        }

        final Object batchLock = new Object();
        final AtomicInteger remaining = new AtomicInteger(list.size());

        for (Runnable t : list) {
            submit(() -> {
                try {
                    t.run();
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        synchronized (batchLock) {
                            batchLock.notifyAll();
                        }
                    }
                }
            });
        }

        synchronized (batchLock) {
            while (remaining.get() > 0) {
                try {
                    batchLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for submitAll()", e);
                }
            }
        }
    }

    public void shutdown() throws InterruptedException {
        // TODO
        synchronized (this) {
            accepting = false;
            this.notifyAll(); // wake any threads blocked in submit()

            while (inFlight.get() > 0) {
                this.wait(); // onTaskDone() will notifyAll()
            }
        }

        for (TiredThread w : workers) {
            w.shutdown();
        }
        for (TiredThread w : workers) {
            w.join();
        }
    }

    public synchronized String getWorkerReport() {
        // TODO: return readable statistics for each worker
        StringBuilder sb = new StringBuilder();
        sb.append("TiredExecutor worker report\n");
        sb.append("inFlight=").append(inFlight.get())
          .append(", accepting=").append(accepting)
          .append("\n");

        for (TiredThread w : workers) {
            double million = 1_000_000.0;
            double usedMs = w.getTimeUsed() / million ;
            double idleMs = w.getTimeIdle() / million;
            double fatigueMs = w.getFatigue() / million;

            sb.append(String.format(
                    Locale.US,
                    "Worker %d (%s): busy=%s, used=%.3fms, idle=%.3fms, fatigue=%.3fms%n",
                    w.getWorkerId(),
                    w.getName(),
                    w.isBusy(),
                    usedMs,
                    idleMs,
                    fatigueMs
            ));
        }
        return sb.toString();
    }

    

    private void cleanUp(TiredThread worker) {
        synchronized (this) {
            inFlight.decrementAndGet();
            if (accepting) {
                idleMinHeap.offer(worker);
            }
            this.notifyAll();
        }
    }
}
