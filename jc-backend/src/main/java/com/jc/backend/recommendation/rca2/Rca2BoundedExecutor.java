package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class Rca2BoundedExecutor implements AutoCloseable {
    public record Completion<T>(Rca2RuntimeContracts.ExecutionStatus status, T value, Throwable error, long elapsedMillis) {}
    private record QueuedTask<T>(long enqueuedAtNanos, Callable<T> callable, Consumer<Completion<T>> completion) {}

    private final ArrayBlockingQueue<QueuedTask<?>> queue;
    private final List<Thread> dispatchers = new ArrayList<>();
    private final ThreadPoolExecutor runtimeExecutor;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong lateDiscarded = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger outstanding = new AtomicInteger();

    public Rca2BoundedExecutor() {
        queue = new ArrayBlockingQueue<>(Rca2RuntimeContracts.MAX_SHADOW_QUEUE_DEPTH);
        runtimeExecutor = new ThreadPoolExecutor(
                Rca2RuntimeContracts.MAX_SHADOW_CONCURRENCY,
                Rca2RuntimeContracts.MAX_SHADOW_CONCURRENCY,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Rca2RuntimeContracts.MAX_SHADOW_CONCURRENCY),
                namedDaemonFactory("jc-rca2-runtime-"),
                new ThreadPoolExecutor.AbortPolicy());
        runtimeExecutor.prestartAllCoreThreads();
        for (int i = 0; i < Rca2RuntimeContracts.MAX_SHADOW_CONCURRENCY; i++) {
            Thread thread = namedDaemonFactory("jc-rca2-dispatch-").newThread(this::dispatchLoop);
            dispatchers.add(thread);
            thread.start();
        }
    }

    public <T> boolean submit(Callable<T> callable, Consumer<Completion<T>> completion) {
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(completion, "completion");
        if (closed.get()) return false;
        QueuedTask<T> task = new QueuedTask<>(System.nanoTime(), callable, completion);
        outstanding.incrementAndGet();
        try {
            boolean accepted = queue.offer(task, Rca2RuntimeContracts.TASK_QUEUE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!accepted) {
                outstanding.decrementAndGet();
                rejected.incrementAndGet();
            }
            return accepted;
        } catch (InterruptedException exception) {
            outstanding.decrementAndGet();
            Thread.currentThread().interrupt();
            rejected.incrementAndGet();
            return false;
        }
    }

    private void dispatchLoop() {
        while (!closed.get() || !queue.isEmpty()) {
            try {
                QueuedTask<?> task = queue.poll(50, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                execute(task);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void execute(QueuedTask<?> raw) {
        QueuedTask<T> task = (QueuedTask<T>) raw;
        try {
            long age = System.nanoTime() - task.enqueuedAtNanos();
            if (age > Rca2RuntimeContracts.MAX_TASK_AGE.toNanos()) {
                lateDiscarded.incrementAndGet();
                task.completion().accept(new Completion<>(Rca2RuntimeContracts.ExecutionStatus.LATE_DISCARDED,
                        null, null, TimeUnit.NANOSECONDS.toMillis(age)));
                return;
            }
            active.incrementAndGet();
            long started = System.nanoTime();
            Future<T> future;
            try {
                future = runtimeExecutor.submit(task.callable());
            } catch (RejectedExecutionException exception) {
                rejected.incrementAndGet();
                task.completion().accept(new Completion<>(Rca2RuntimeContracts.ExecutionStatus.EXCEPTION,
                        null, exception, elapsed(started)));
                return;
            }
            try {
                T result = future.get(Rca2RuntimeContracts.TOTAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                long elapsed = elapsed(started);
                if (elapsed > Rca2RuntimeContracts.TOTAL_TIMEOUT.toMillis()) {
                    lateDiscarded.incrementAndGet();
                    task.completion().accept(new Completion<>(Rca2RuntimeContracts.ExecutionStatus.LATE_DISCARDED,
                            null, null, elapsed));
                } else {
                    task.completion().accept(new Completion<>(Rca2RuntimeContracts.ExecutionStatus.SUCCESS,
                            result, null, elapsed));
                }
            } catch (TimeoutException exception) {
                timedOut.incrementAndGet();
                future.cancel(true);
                task.completion().accept(new Completion<>(Rca2RuntimeContracts.ExecutionStatus.TIMEOUT,
                        null, exception, elapsed(started)));
            } catch (InterruptedException exception) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                task.completion().accept(new Completion<>(Rca2RuntimeContracts.ExecutionStatus.CANCELLED,
                        null, exception, elapsed(started)));
            } catch (ExecutionException exception) {
                task.completion().accept(new Completion<>(Rca2RuntimeContracts.ExecutionStatus.EXCEPTION,
                        null, exception.getCause(), elapsed(started)));
            } finally {
                active.decrementAndGet();
            }
        } finally {
            outstanding.decrementAndGet();
        }
    }

    public int queueDepth() { return queue.size(); }
    public int activeCount() { return active.get(); }
    public int outstandingCount() { return outstanding.get(); }
    public long rejectedCount() { return rejected.get(); }
    public long lateDiscardedCount() { return lateDiscarded.get(); }
    public long timeoutCount() { return timedOut.get(); }

    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (outstanding.get() == 0 && queue.isEmpty() && active.get() == 0
                    && runtimeExecutor.getActiveCount() == 0) return true;
            try { Thread.sleep(2L); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Thread dispatcher : dispatchers) dispatcher.interrupt();
        List<QueuedTask<?>> abandoned = new ArrayList<>();
        queue.drainTo(abandoned);
        outstanding.addAndGet(-abandoned.size());
        runtimeExecutor.shutdownNow();
        try { runtimeExecutor.awaitTermination(1, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - started));
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) -> { });
            return thread;
        };
    }
}
