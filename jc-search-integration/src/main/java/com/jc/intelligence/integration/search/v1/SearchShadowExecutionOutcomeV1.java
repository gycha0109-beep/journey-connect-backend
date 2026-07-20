package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.runtime.search.v1.SearchRuntimeResultV1;
import java.time.Duration;

public record SearchShadowExecutionOutcomeV1(
        SearchShadowExecutionStatus status,
        SearchRuntimeResultV1 runtimeResult,
        String safeFailureCode,
        Duration runtimeDuration) {
    public SearchShadowExecutionOutcomeV1 {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (runtimeDuration == null || runtimeDuration.isNegative()) {
            throw new IllegalArgumentException("runtimeDuration must be nonnegative");
        }
        if (status == SearchShadowExecutionStatus.COMPLETED) {
            if (runtimeResult == null || safeFailureCode != null) {
                throw new IllegalArgumentException("completed outcome requires runtime result only");
            }
        } else {
            if (runtimeResult != null) throw new IllegalArgumentException("non-completed outcome cannot carry runtime result");
            safeFailureCode = requireSafeCode(safeFailureCode);
        }
    }

    public static SearchShadowExecutionOutcomeV1 completed(SearchRuntimeResultV1 result, Duration duration) {
        return new SearchShadowExecutionOutcomeV1(SearchShadowExecutionStatus.COMPLETED, result, null, duration);
    }
    public static SearchShadowExecutionOutcomeV1 timedOut(Duration duration) {
        return new SearchShadowExecutionOutcomeV1(SearchShadowExecutionStatus.TIMED_OUT, null, "runtime_timeout", duration);
    }
    public static SearchShadowExecutionOutcomeV1 failed(String code, Duration duration) {
        return new SearchShadowExecutionOutcomeV1(SearchShadowExecutionStatus.FAILED, null, code, duration);
    }

    private static String requireSafeCode(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeFailureCode must be lowercase_snake_case");
        }
        return value;
    }
}
