package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.v1.version.ContractId;

public final class SearchContractIds {
    public static final ContractId SEARCH_DOMAIN = new ContractId("search-domain-contract-v1");
    public static final ContractId SEARCH_QUERY_NORMALIZATION = new ContractId("search-query-normalization-v1");
    public static final ContractId SEARCH_RETRIEVAL_RANKING = new ContractId("search-retrieval-ranking-v1");
    public static final ContractId SEARCH_PAGINATION_CURSOR = new ContractId("search-pagination-cursor-v1");
    public static final ContractId SEARCH_REPLAY_EVIDENCE = new ContractId("search-replay-evidence-v1");

    private SearchContractIds() {
    }
}
