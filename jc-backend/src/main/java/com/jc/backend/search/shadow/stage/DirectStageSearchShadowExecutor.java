package com.jc.backend.search.shadow.stage;

import com.jc.intelligence.wiring.search.v1.SearchShadowExecutor;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutorResultV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutorStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowTask;
import java.time.Duration;

/** Executes integration synchronously only after the outer stage dispatch has left the request thread. */
public final class DirectStageSearchShadowExecutor implements SearchShadowExecutor {
    private final int queueCapacity;
    private final int maxConcurrency;

    public DirectStageSearchShadowExecutor(int queueCapacity, int maxConcurrency) {
        if (queueCapacity < 1 || maxConcurrency < 1) throw new IllegalArgumentException("executor bounds must be positive");
        this.queueCapacity = queueCapacity;
        this.maxConcurrency = maxConcurrency;
    }

    @Override public boolean available() { return true; }
    @Override public int queueCapacity() { return queueCapacity; }
    @Override public int maxConcurrency() { return maxConcurrency; }

    @Override public <T> SearchShadowExecutorResultV1<T> submit(SearchShadowTask<T> task, Duration timeout) {
        if (task == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("task and positive timeout are required");
        }
        try {
            return SearchShadowExecutorResultV1.completed(task.execute());
        } catch (RuntimeException exception) {
            return SearchShadowExecutorResultV1.failure(SearchShadowExecutorStatus.FAILED, "stage_dispatch_failed");
        }
    }
}
