package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.PolicyVersion;

public final class LegacyExploreContractIds {
    public static final ContractId ADAPTER = new ContractId("legacy-explore-read-adapter-v1");
    public static final PolicyVersion MAPPING_POLICY = new PolicyVersion("legacy-explore-mapping-policy-v1");
    public static final PolicyVersion ORDER_POLICY = new PolicyVersion("legacy-explore-order-v1");
    public static final String ENDPOINT_ID = "get-api-v1-explore";

    private LegacyExploreContractIds() { }
}
