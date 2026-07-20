package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;

public final class SearchReadinessContractIds {
    public static final ContractId READINESS = new ContractId("search-shadow-activation-readiness-v1");
    public static final ContractId PREREQUISITE_MATRIX = new ContractId("search-shadow-activation-prerequisite-matrix-v1");
    public static final ContractId BUDGET = new ContractId("search-shadow-execution-budget-v1");
    public static final ContractId KILL_SWITCH = new ContractId("search-shadow-kill-switch-v1");
    public static final ContractId ROLLBACK = new ContractId("search-shadow-rollback-v1");
    public static final ContractId OBSERVABILITY = new ContractId("search-shadow-observability-retention-v1");
    public static final ContractId REGRESSION = new ContractId("ip-8-search-regression-closure-v1");
    private SearchReadinessContractIds() { }
}
