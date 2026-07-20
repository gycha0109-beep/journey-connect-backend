package com.jc.intelligence.runtime.search.v1.fixture;

public record SearchRuntimeFixtureCandidateV1(
        String entityRef,
        int sourceRank,
        Double rankingScore,
        String orderingKey,
        String eligibilityDecision,
        String visibilityDecision) {
    public SearchRuntimeFixtureCandidateV1 {
        if (entityRef == null || !entityRef.matches("[a-z][a-z0-9_-]*:[^\s:][^\s]*")) {
            throw new IllegalArgumentException("entityRef must be namespaced");
        }
        if (sourceRank < 1) throw new IllegalArgumentException("sourceRank must be 1-based");
        if (rankingScore != null && !Double.isFinite(rankingScore.doubleValue())) {
            throw new IllegalArgumentException("rankingScore must be finite");
        }
        if (orderingKey != null && (orderingKey.isBlank() || !orderingKey.equals(orderingKey.trim()))) {
            throw new IllegalArgumentException("orderingKey must be trimmed when present");
        }
        if (!java.util.Set.of("allow", "deny", "unknown", "dependency_unavailable").contains(eligibilityDecision)
                || !java.util.Set.of("allow", "deny", "unknown", "dependency_unavailable").contains(visibilityDecision)) {
            throw new IllegalArgumentException("unsupported dependency decision");
        }
    }
}
