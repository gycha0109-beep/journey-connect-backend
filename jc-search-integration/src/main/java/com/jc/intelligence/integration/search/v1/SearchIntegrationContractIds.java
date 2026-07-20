package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;

public final class SearchIntegrationContractIds {
    public static final ContractId INTEGRATION_BOUNDARY =
            new ContractId("search-runtime-integration-boundary-v1");
    public static final ContractId COMPARISON_EVIDENCE =
            new ContractId("search-shadow-comparison-evidence-v1");

    private SearchIntegrationContractIds() { }
}
