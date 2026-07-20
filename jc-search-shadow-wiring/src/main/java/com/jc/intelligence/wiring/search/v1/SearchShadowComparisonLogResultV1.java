package com.jc.intelligence.wiring.search.v1;

public record SearchShadowComparisonLogResultV1(SearchShadowLogStatus status, String safeCode) {
    public SearchShadowComparisonLogResultV1 {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (safeCode != null && !safeCode.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeCode must be lowercase_snake_case");
        }
    }
}
