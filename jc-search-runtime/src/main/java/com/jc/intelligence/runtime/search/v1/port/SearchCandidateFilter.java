package com.jc.intelligence.runtime.search.v1.port;

import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;

@FunctionalInterface
public interface SearchCandidateFilter {
    SearchFilterDecisionV1 evaluate(SearchRequestV1 request, RetrievalCandidateV1 candidate);
}
