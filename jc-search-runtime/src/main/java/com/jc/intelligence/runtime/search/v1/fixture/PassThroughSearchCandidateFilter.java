package com.jc.intelligence.runtime.search.v1.fixture;

import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.runtime.search.v1.port.SearchCandidateFilter;
import com.jc.intelligence.runtime.search.v1.port.SearchFilterDecisionV1;

public final class PassThroughSearchCandidateFilter implements SearchCandidateFilter {
    @Override public SearchFilterDecisionV1 evaluate(SearchRequestV1 request, RetrievalCandidateV1 candidate) {
        return SearchFilterDecisionV1.include();
    }
}
