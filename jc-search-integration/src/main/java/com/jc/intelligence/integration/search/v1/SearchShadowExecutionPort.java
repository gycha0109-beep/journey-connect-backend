package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;

@FunctionalInterface
public interface SearchShadowExecutionPort {
    SearchShadowExecutionOutcomeV1 execute(
            SearchRuntime runtime,
            SearchRuntimeExecutionRequestV1 request,
            SearchShadowExecutionDeadlineV1 deadline);
}
