package com.jc.intelligence.wiring.search.v1;

public record SearchShadowExecutorResultV1<T>(SearchShadowExecutorStatus status, T value, String safeFailureCode) {
    public SearchShadowExecutorResultV1 {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (status == SearchShadowExecutorStatus.COMPLETED && value == null) throw new IllegalArgumentException("completed executor result requires value");
        if (status != SearchShadowExecutorStatus.COMPLETED && value != null) throw new IllegalArgumentException("non-completed executor result cannot expose value");
        if (safeFailureCode != null && !safeFailureCode.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeFailureCode must be lowercase_snake_case");
        }
    }
    public static <T> SearchShadowExecutorResultV1<T> completed(T value) {
        return new SearchShadowExecutorResultV1<>(SearchShadowExecutorStatus.COMPLETED, value, null);
    }
    public static <T> SearchShadowExecutorResultV1<T> failure(SearchShadowExecutorStatus status, String code) {
        return new SearchShadowExecutorResultV1<>(status, null, code);
    }
}
