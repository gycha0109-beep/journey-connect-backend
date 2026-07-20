package com.jc.backend.search.shadow.production;

import com.jc.intelligence.integration.search.v1.SearchShadowExecutionDeadlineV1;
import com.jc.intelligence.integration.search.v1.SearchShadowExecutionOutcomeV1;
import com.jc.intelligence.integration.search.v1.SearchShadowExecutionPort;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import java.time.Duration;

/** Executes inside the already bounded production worker. The outer watchdog owns cancellation. */
public final class DirectProductionSearchShadowExecutionPort implements SearchShadowExecutionPort {
    @Override
    public SearchShadowExecutionOutcomeV1 execute(
            SearchRuntime runtime,
            SearchRuntimeExecutionRequestV1 request,
            SearchShadowExecutionDeadlineV1 deadline) {
        if (runtime == null || request == null || deadline == null) {
            throw new IllegalArgumentException("runtime execution inputs are required");
        }
        long started = System.nanoTime();
        try {
            var result = runtime.execute(request);
            Duration elapsed = elapsed(started);
            if (elapsed.compareTo(deadline.timeout()) > 0 || Thread.currentThread().isInterrupted()) {
                return SearchShadowExecutionOutcomeV1.timedOut(elapsed);
            }
            return SearchShadowExecutionOutcomeV1.completed(result, elapsed);
        } catch (RuntimeException exception) {
            return Thread.currentThread().isInterrupted()
                    ? SearchShadowExecutionOutcomeV1.timedOut(elapsed(started))
                    : SearchShadowExecutionOutcomeV1.failed("runtime_failed", elapsed(started));
        }
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - started));
    }
}
