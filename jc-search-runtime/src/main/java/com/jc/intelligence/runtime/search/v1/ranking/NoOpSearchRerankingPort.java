package com.jc.intelligence.runtime.search.v1.ranking;

import java.util.List;

public final class NoOpSearchRerankingPort implements SearchRerankingPort {
    @Override
    public List<SearchRankedCandidateV1> rerank(
            SearchRankingRequestV1 request,
            List<SearchRankedCandidateV1> candidates) {
        return List.copyOf(candidates);
    }
}
