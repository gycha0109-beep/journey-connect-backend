package com.jc.intelligence.runtime.search.v1.ranking;

import java.util.List;
import java.util.Objects;

public record SearchRankingResultV1(
        SearchRankingStatus status,
        List<SearchRankedCandidateV1> candidates,
        String safeFailureCategory) {
    public SearchRankingResultV1 {
        Objects.requireNonNull(status, "status");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (status == SearchRankingStatus.SUCCESS && safeFailureCategory != null) {
            throw new IllegalArgumentException("successful ranking cannot include a failure category");
        }
        if (status == SearchRankingStatus.FAILED && (safeFailureCategory == null || safeFailureCategory.isBlank())) {
            throw new IllegalArgumentException("failed ranking requires a safe category");
        }
    }
    public static SearchRankingResultV1 success(List<SearchRankedCandidateV1> candidates) {
        return new SearchRankingResultV1(SearchRankingStatus.SUCCESS, candidates, null);
    }
    public static SearchRankingResultV1 failed(String category) {
        return new SearchRankingResultV1(SearchRankingStatus.FAILED, List.of(), category);
    }
}
