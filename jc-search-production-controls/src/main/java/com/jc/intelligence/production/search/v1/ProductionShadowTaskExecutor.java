package com.jc.intelligence.production.search.v1;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ProductionShadowTaskExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor watchdog;
    private final Duration shutdownGrace;

    public ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1 policy) {
        Objects.requireNonNull(policy, "policy");
        shutdownGrace = policy.shutdownGracePeriod();
        executor = new ThreadPoolExecutor(
                policy.coreConcurrency(),
                policy.maxConcurrency(),
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(policy.queueCapacity()),
                namedDaemonFactory("jc-search-shadow-prod-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        watchdog = new ScheduledThreadPoolExecutor(1, namedDaemonFactory("jc-search-shadow-watchdog-"));
        watchdog.setRemoveOnCancelPolicy(true);
        watchdog.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    public ProductionShadowDispatchStatus submit(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task required");
        }
        if (executor.isShutdown()) {
            return ProductionShadowDispatchStatus.EXECUTOR_UNAVAILABLE;
        }
        try {
            executor.execute(task);
            return ProductionShadowDispatchStatus.SUBMITTED;
        } catch (RejectedExecutionException exception) {
            return executor.getQueue().remainingCapacity() == 0
                    ? ProductionShadowDispatchStatus.QUEUE_FULL
                    : ProductionShadowDispatchStatus.REJECTED;
        }
    }

    public ProductionShadowDispatchStatus submitTimed(
            Runnable task,
            Duration runtimeTimeout,
            Duration hardCancellationTimeout,
            Consumer<ProductionShadowTaskCompletionV1> completion) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(runtimeTimeout, "runtimeTimeout");
        Objects.requireNonNull(hardCancellationTimeout, "hardCancellationTimeout");
        Objects.requireNonNull(completion, "completion");
        if (runtimeTimeout.isZero() || runtimeTimeout.isNegative()
                || hardCancellationTimeout.compareTo(runtimeTimeout) < 0) {
            throw new IllegalArgumentException("timed execution bounds invalid");
        }
        return submit(() -> executeTimed(task, runtimeTimeout, hardCancellationTimeout, completion));
    }

    private void executeTimed(
            Runnable task,
            Duration runtimeTimeout,
            Duration hardCancellationTimeout,
            Consumer<ProductionShadowTaskCompletionV1> completion) {
        long started = System.nanoTime();
        Thread worker = Thread.currentThread();
        AtomicBoolean finished = new AtomicBoolean();
        AtomicBoolean completionReported = new AtomicBoolean();
        ScheduledFuture<?> timeoutFuture = watchdog.schedule(() -> {
            if (!finished.get()) {
                worker.interrupt();
                if (completionReported.compareAndSet(false, true)) {
                    safeComplete(completion, new ProductionShadowTaskCompletionV1(
                            ProductionShadowTaskCompletionStatus.TIMED_OUT, elapsed(started), "runtime_timeout"));
                }
            }
        }, runtimeTimeout.toNanos(), TimeUnit.NANOSECONDS);
        ScheduledFuture<?> hardFuture = watchdog.schedule(() -> {
            if (!finished.get()) {
                worker.interrupt();
            }
        }, hardCancellationTimeout.toNanos(), TimeUnit.NANOSECONDS);
        try {
            task.run();
            if (completionReported.compareAndSet(false, true)) {
                safeComplete(completion, new ProductionShadowTaskCompletionV1(
                        ProductionShadowTaskCompletionStatus.COMPLETED, elapsed(started), "completed"));
            }
        } catch (RuntimeException exception) {
            if (completionReported.compareAndSet(false, true)) {
                safeComplete(completion, new ProductionShadowTaskCompletionV1(
                        ProductionShadowTaskCompletionStatus.FAILED, elapsed(started), "shadow_task_failed"));
            }
        } finally {
            finished.set(true);
            timeoutFuture.cancel(false);
            hardFuture.cancel(false);
            Thread.interrupted();
        }
    }

    private static void safeComplete(
            Consumer<ProductionShadowTaskCompletionV1> completion,
            ProductionShadowTaskCompletionV1 outcome) {
        try {
            completion.accept(outcome);
        } catch (RuntimeException ignored) {
            // Completion observation never acquires legacy response authority.
        }
    }

    public int queueDepth() {
        return executor.getQueue().size();
    }

    public int activeCount() {
        return executor.getActiveCount();
    }

    public int maximumConcurrency() {
        return executor.getMaximumPoolSize();
    }

    public int queueCapacity() {
        return executor.getQueue().size() + executor.getQueue().remainingCapacity();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void close() {
        watchdog.shutdownNow();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - started));
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignoredThread, ignoredError) -> { });
            return thread;
        };
    }
}
