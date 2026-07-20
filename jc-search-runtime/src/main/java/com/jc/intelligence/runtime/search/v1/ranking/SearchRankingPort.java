package com.jc.intelligence.runtime.search.v1.ranking;

@FunctionalInterface
public interface SearchRankingPort {
    SearchRankingResultV1 rank(SearchRankingRequestV1 request);
}
