package com.jc.intelligence.runtime.search.v1.port;

import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record SearchRetrievalResultV1(
        SearchRetrievalStatus status,
        List<RetrievalCandidateV1> candidates,
        String safeFailureCategory) {
    public SearchRetrievalResultV1 {
        Objects.requireNonNull(status, "status");
        candidates = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(candidates, "candidates")));
        if (status == SearchRetrievalStatus.SUCCESS && safeFailureCategory != null) {
            throw new IllegalArgumentException("successful retrieval cannot include a failure category");
        }
        if (status != SearchRetrievalStatus.SUCCESS && (safeFailureCategory == null || safeFailureCategory.isBlank())) {
            throw new IllegalArgumentException("failed/unavailable retrieval requires a safe category");
        }
    }

    public static SearchRetrievalResultV1 success(List<RetrievalCandidateV1> candidates) {
        return new SearchRetrievalResultV1(SearchRetrievalStatus.SUCCESS, candidates, null);
    }
    public static SearchRetrievalResultV1 unavailable(String category) {
        return new SearchRetrievalResultV1(SearchRetrievalStatus.UNAVAILABLE, List.of(), category);
    }
    public static SearchRetrievalResultV1 failed(String category) {
        return new SearchRetrievalResultV1(SearchRetrievalStatus.FAILED, List.of(), category);
    }
}
