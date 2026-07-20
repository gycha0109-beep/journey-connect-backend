package com.jc.backend.search.shadow.stage;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded daemon executor used only by an explicitly active test/stage context. */
public final class StageSearchShadowTaskExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final int maxConcurrency;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public StageSearchShadowTaskExecutor(int maxConcurrency, int queueCapacity) {
        if (maxConcurrency < 1 || maxConcurrency > 16) throw new IllegalArgumentException("maxConcurrency must be 1..16");
        if (queueCapacity < 1 || queueCapacity > 128) throw new IllegalArgumentException("queueCapacity must be 1..128");
        this.queueCapacity = queueCapacity;
        this.maxConcurrency = maxConcurrency;
        this.executor = new ThreadPoolExecutor(maxConcurrency, maxConcurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), namedDaemonFactory("jc-search-shadow-stage-dispatch-"),
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.prestartAllCoreThreads();
    }

    public StageSearchShadowSubmissionResult submit(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task is required");
        if (executor.isShutdown()) {
            return new StageSearchShadowSubmissionResult(StageSearchShadowSubmissionStatus.EXECUTOR_UNAVAILABLE,
                    "executor_unavailable");
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                    completed.incrementAndGet();
                } catch (RuntimeException exception) {
                    failed.incrementAndGet();
                }
            });
            accepted.incrementAndGet();
            return new StageSearchShadowSubmissionResult(StageSearchShadowSubmissionStatus.ACCEPTED, "submitted");
        } catch (RejectedExecutionException exception) {
            if (executor.isShutdown()) {
                return new StageSearchShadowSubmissionResult(StageSearchShadowSubmissionStatus.EXECUTOR_UNAVAILABLE,
                        "executor_unavailable");
            }
            if (executor.getQueue().remainingCapacity() == 0) {
                return new StageSearchShadowSubmissionResult(StageSearchShadowSubmissionStatus.QUEUE_FULL, "queue_full");
            }
            return new StageSearchShadowSubmissionResult(StageSearchShadowSubmissionStatus.REJECTED, "rejected");
        }
    }

    public boolean available() { return !executor.isShutdown(); }
    public int queueCapacity() { return queueCapacity; }
    public int maxConcurrency() { return maxConcurrency; }
    public long acceptedCount() { return accepted.get(); }
    public long completedCount() { return completed.get(); }
    public long failedCount() { return failed.get(); }
    public int queuedTaskCount() { return executor.getQueue().size(); }
    public int activeTaskCount() { return executor.getActiveCount(); }

    public boolean awaitIdle(Duration timeout) {
        if (timeout == null || timeout.isNegative()) throw new IllegalArgumentException("timeout must be nonnegative");
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() <= deadline) {
            if (activeTaskCount() == 0 && queuedTaskCount() == 0 && completed.get() + failed.get() >= accepted.get()) {
                return true;
            }
            try {
                Thread.sleep(2L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, exception) -> { });
            return thread;
        };
    }
}
