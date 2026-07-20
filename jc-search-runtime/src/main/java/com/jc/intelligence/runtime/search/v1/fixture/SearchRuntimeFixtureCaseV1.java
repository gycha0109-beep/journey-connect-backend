package com.jc.intelligence.runtime.search.v1.fixture;

import java.util.List;

public record SearchRuntimeFixtureCaseV1(
        String scenario,
        String rawQuery,
        int pageSize,
        int maximumCandidateCount,
        String retrievalStatus,
        String rankingStatus,
        boolean reverseReranking,
        List<SearchRuntimeFixtureCandidateV1> candidates) {
    public SearchRuntimeFixtureCaseV1 {
        if (scenario == null || scenario.isBlank() || !scenario.equals(scenario.trim())) {
            throw new IllegalArgumentException("scenario is required");
        }
        if (rawQuery == null || rawQuery.isBlank()) throw new IllegalArgumentException("rawQuery is required");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be 1..100");
        if (maximumCandidateCount < 1 || maximumCandidateCount > 1000) {
            throw new IllegalArgumentException("maximumCandidateCount must be 1..1000");
        }
        if (!java.util.Set.of("success", "unavailable", "failed").contains(retrievalStatus)) {
            throw new IllegalArgumentException("unsupported retrievalStatus");
        }
        if (!java.util.Set.of("success", "failed").contains(rankingStatus)) {
            throw new IllegalArgumentException("unsupported rankingStatus");
        }
        candidates = List.copyOf(candidates);
    }
}
