package com.jc.intelligence.runtime.search.v1.ranking;

import java.util.List;

@FunctionalInterface
public interface SearchRerankingPort {
    List<SearchRankedCandidateV1> rerank(SearchRankingRequestV1 request, List<SearchRankedCandidateV1> candidates);
}
