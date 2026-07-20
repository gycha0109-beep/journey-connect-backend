package com.jc.backend.search.shadow.stage;

import com.jc.intelligence.integration.search.v1.SearchShadowExecutionDeadlineV1;
import com.jc.intelligence.integration.search.v1.SearchShadowExecutionOutcomeV1;
import com.jc.intelligence.integration.search.v1.SearchShadowExecutionPort;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeResultV1;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Wall-clock bounded runtime execution for test/stage shadow tasks. */
public final class StageBoundedSearchShadowExecutionPort implements SearchShadowExecutionPort, AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final AtomicLong invocations = new AtomicLong();
    private final AtomicLong cancellations = new AtomicLong();

    public StageBoundedSearchShadowExecutionPort(int maxConcurrency, int queueCapacity) {
        if (maxConcurrency < 1 || maxConcurrency > 16) throw new IllegalArgumentException("maxConcurrency must be 1..16");
        if (queueCapacity < 1 || queueCapacity > 128) throw new IllegalArgumentException("queueCapacity must be 1..128");
        executor = new ThreadPoolExecutor(maxConcurrency, maxConcurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), namedDaemonFactory("jc-search-shadow-stage-runtime-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.prestartAllCoreThreads();
    }

    @Override public SearchShadowExecutionOutcomeV1 execute(
            SearchRuntime runtime,
            SearchRuntimeExecutionRequestV1 request,
            SearchShadowExecutionDeadlineV1 deadline) {
        if (runtime == null || request == null || deadline == null) {
            throw new IllegalArgumentException("runtime execution inputs are required");
        }
        invocations.incrementAndGet();
        long started = System.nanoTime();
        final Future<SearchRuntimeResultV1> future;
        try {
            future = executor.submit(() -> runtime.execute(request));
        } catch (RejectedExecutionException exception) {
            return SearchShadowExecutionOutcomeV1.failed(
                    executor.isShutdown() ? "runtime_executor_unavailable" : "runtime_queue_full", elapsed(started));
        }
        try {
            SearchRuntimeResultV1 result = future.get(deadline.timeout().toNanos(), TimeUnit.NANOSECONDS);
            return SearchShadowExecutionOutcomeV1.completed(result, elapsed(started));
        } catch (TimeoutException exception) {
            cancellations.incrementAndGet();
            future.cancel(true);
            return SearchShadowExecutionOutcomeV1.timedOut(elapsed(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancellations.incrementAndGet();
            future.cancel(true);
            return SearchShadowExecutionOutcomeV1.failed("runtime_cancelled", elapsed(started));
        } catch (ExecutionException exception) {
            return SearchShadowExecutionOutcomeV1.failed("runtime_failed", elapsed(started));
        }
    }

    public long invocationCount() { return invocations.get(); }
    public long cancellationCount() { return cancellations.get(); }
    public boolean available() { return !executor.isShutdown(); }

    @Override public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
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
            thread.setUncaughtExceptionHandler((ignored, exception) -> { });
            return thread;
        };
    }
}
