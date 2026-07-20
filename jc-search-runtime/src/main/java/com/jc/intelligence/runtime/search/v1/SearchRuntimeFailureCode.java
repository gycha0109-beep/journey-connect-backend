package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchRuntimeFailureCode implements WireValue {
    INVALID_REQUEST("invalid_request"),
    QUERY_VALIDATION_FAILED("query_validation_failed"),
    RETRIEVAL_UNAVAILABLE("retrieval_unavailable"),
    RETRIEVAL_FAILED("retrieval_failed"),
    INVALID_CANDIDATE("invalid_candidate"),
    DUPLICATE_CANDIDATE("duplicate_candidate"),
    ELIGIBILITY_DEPENDENCY_UNAVAILABLE("eligibility_dependency_unavailable"),
    VISIBILITY_DEPENDENCY_UNAVAILABLE("visibility_dependency_unavailable"),
    FILTERING_FAILED("filtering_failed"),
    RANKING_FAILED("ranking_failed"),
    INVALID_SCORE("invalid_score"),
    RERANKING_FAILED("reranking_failed"),
    ORDERING_FAILED("ordering_failed"),
    SNAPSHOT_FAILED("snapshot_failed"),
    CONTRACT_VALIDATION_FAILED("contract_validation_failed"),
    CURSOR_UNSUPPORTED("cursor_unsupported");

    private final String wireValue;

    SearchRuntimeFailureCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
