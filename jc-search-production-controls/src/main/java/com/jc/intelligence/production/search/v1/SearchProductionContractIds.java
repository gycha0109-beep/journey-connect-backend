package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;

public final class SearchProductionContractIds {
    public static final ContractId TECHNICAL_CONTROLS = new ContractId("ip-11-5-production-shadow-technical-controls-v1");
    public static final SchemaVersion PROJECTION_SCHEMA = new SchemaVersion("search-document-projection-v1");
    public static final PolicyVersion ELIGIBILITY_POLICY = new PolicyVersion("search-document-eligibility-v1");
    public static final SchemaVersion RETRIEVAL_STRATEGY = new SchemaVersion("search-document-projection-retrieval-v1");
    public static final PolicyVersion SAMPLING_POLICY = new PolicyVersion("search-production-shadow-sampling-proposed-v1");
    private SearchProductionContractIds() { }
}
