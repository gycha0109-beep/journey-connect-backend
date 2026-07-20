package com.jc.intelligence.contract.v1.search.failure;

import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchFallbackCode;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;

public record SearchFallbackV1(
        ContractId contractVersion,
        RunRef runId,
        SearchFallbackCode fallbackCode,
        SchemaVersion fallbackStrategyVersion,
        String primaryFailureEvidenceRef,
        String orderingContractRef,
        Instant createdAt) {
    public SearchFallbackV1 {
        SearchVersionValidatorV1.requireContract(contractVersion, SearchContractIds.SEARCH_REPLAY_EVIDENCE);
        SearchChecks.requireNonNull(runId, "runId");
        SearchChecks.requireNonNull(fallbackCode, "fallbackCode");
        SearchChecks.requireNonNull(fallbackStrategyVersion, "fallbackStrategyVersion");
        primaryFailureEvidenceRef = SearchChecks.requireOptionalRef(
                SearchChecks.requireText(primaryFailureEvidenceRef, "primaryFailureEvidenceRef"),
                "primaryFailureEvidenceRef");
        orderingContractRef = SearchChecks.requireOptionalRef(
                SearchChecks.requireText(orderingContractRef, "orderingContractRef"),
                "orderingContractRef");
        SearchChecks.requireInstant(createdAt, "createdAt");
    }
}
