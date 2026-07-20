package com.jc.intelligence.integration.search.v1.fixture;

import com.jc.intelligence.integration.search.v1.SearchShadowExecutionDeadlineV1;
import com.jc.intelligence.integration.search.v1.SearchShadowExecutionOutcomeV1;
import com.jc.intelligence.integration.search.v1.SearchShadowExecutionPort;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import java.time.Duration;
import java.util.Objects;

public final class DirectTestSearchShadowExecutionPort implements SearchShadowExecutionPort {
    private final Duration deterministicDuration;

    public DirectTestSearchShadowExecutionPort(Duration deterministicDuration) {
        this.deterministicDuration = Objects.requireNonNull(deterministicDuration, "deterministicDuration");
        if (deterministicDuration.isNegative()) throw new IllegalArgumentException("deterministicDuration must be nonnegative");
    }

    @Override public SearchShadowExecutionOutcomeV1 execute(
            SearchRuntime runtime,
            SearchRuntimeExecutionRequestV1 request,
            SearchShadowExecutionDeadlineV1 deadline) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(deadline, "deadline");
        if (deterministicDuration.compareTo(deadline.timeout()) > 0) {
            return SearchShadowExecutionOutcomeV1.timedOut(deterministicDuration);
        }
        return SearchShadowExecutionOutcomeV1.completed(runtime.execute(request), deterministicDuration);
    }
}
