package com.jc.intelligence.runtime.search.v1.ranking;

import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SearchRankingRequestV1(
        SearchRequestV1 searchRequest,
        List<RetrievalCandidateV1> candidates,
        PolicyVersion rankingPolicyVersion,
        Instant referenceTime) {
    public SearchRankingRequestV1 {
        Objects.requireNonNull(searchRequest, "searchRequest");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(referenceTime, "referenceTime");
    }
}
