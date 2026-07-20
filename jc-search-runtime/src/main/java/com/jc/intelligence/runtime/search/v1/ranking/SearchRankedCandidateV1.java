package com.jc.intelligence.runtime.search.v1.ranking;

import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import java.util.Objects;

public record SearchRankedCandidateV1(
        RetrievalCandidateV1 candidate,
        Double rankingScore,
        String explicitOrderingKey) {
    public SearchRankedCandidateV1 {
        Objects.requireNonNull(candidate, "candidate");
        if (rankingScore != null && !Double.isFinite(rankingScore.doubleValue())) {
            throw new IllegalArgumentException("rankingScore must be finite when present");
        }
        if (explicitOrderingKey != null) {
            if (explicitOrderingKey.isBlank() || !explicitOrderingKey.equals(explicitOrderingKey.trim())
                    || explicitOrderingKey.length() > 256) {
                throw new IllegalArgumentException("explicitOrderingKey must be trimmed nonblank text up to 256 characters");
            }
        }
    }
}
