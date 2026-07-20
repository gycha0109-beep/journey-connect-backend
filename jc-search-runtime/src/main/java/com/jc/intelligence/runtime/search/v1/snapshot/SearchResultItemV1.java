package com.jc.intelligence.runtime.search.v1.snapshot;

import com.jc.intelligence.contract.v1.search.cursor.SearchOrderingTupleV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import java.util.Objects;

public record SearchResultItemV1(
        RetrievalCandidateV1 candidate,
        Double rankingScore,
        String explicitOrderingKey,
        int finalPosition,
        SearchOrderingTupleV1 orderingTuple) {
    public SearchResultItemV1 {
        Objects.requireNonNull(candidate, "candidate");
        if (rankingScore != null && !Double.isFinite(rankingScore.doubleValue())) {
            throw new IllegalArgumentException("rankingScore must be finite when present");
        }
        if (finalPosition < 1) throw new IllegalArgumentException("finalPosition is 1-based");
        Objects.requireNonNull(orderingTuple, "orderingTuple");
    }
}
