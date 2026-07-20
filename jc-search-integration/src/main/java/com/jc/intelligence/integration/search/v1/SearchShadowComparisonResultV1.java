package com.jc.intelligence.integration.search.v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SearchShadowComparisonResultV1(
        SearchShadowComparisonStatus status,
        SearchShadowComparisonMetricsV1 metrics,
        List<SearchShadowMismatchV1> mismatches) {
    public SearchShadowComparisonResultV1 {
        if (status == null || metrics == null) throw new IllegalArgumentException("status and metrics are required");
        ArrayList<SearchShadowMismatchV1> sorted = new ArrayList<>(mismatches == null ? List.of() : mismatches);
        if (sorted.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("mismatches contain null");
        Collections.sort(sorted);
        if (new java.util.LinkedHashSet<>(sorted).size() != sorted.size()) {
            throw new IllegalArgumentException("mismatches must be unique");
        }
        mismatches = List.copyOf(sorted);
        if (status == SearchShadowComparisonStatus.FAILED
                && mismatches.stream().noneMatch(item -> item.code() == SearchShadowMismatchCode.COMPARISON_FAILURE)) {
            throw new IllegalArgumentException("failed comparison requires comparison_failure mismatch");
        }
    }

    public SearchShadowSeverity maximumSeverity() {
        SearchShadowSeverity result = SearchShadowSeverity.INFO;
        for (SearchShadowMismatchV1 mismatch : mismatches) {
            if (rank(mismatch.severity()) > rank(result)) result = mismatch.severity();
        }
        return result;
    }

    private static int rank(SearchShadowSeverity severity) {
        return switch (severity) {
            case INFO -> 0;
            case NOT_COMPARABLE -> 1;
            case WARNING -> 2;
            case ERROR -> 3;
        };
    }
}
