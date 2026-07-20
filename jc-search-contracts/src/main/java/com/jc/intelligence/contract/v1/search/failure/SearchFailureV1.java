package com.jc.intelligence.contract.v1.search.failure;

import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchFailureCode;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.ContractId;
import java.time.Instant;

public record SearchFailureV1(
        ContractId contractVersion,
        RunRef runId,
        SearchFailureCode failureCode,
        String evidenceRef,
        boolean retryable,
        Instant occurredAt) {
    public SearchFailureV1 {
        SearchVersionValidatorV1.requireContract(contractVersion, SearchContractIds.SEARCH_REPLAY_EVIDENCE);
        SearchChecks.requireNonNull(failureCode, "failureCode");
        if (evidenceRef != null) evidenceRef = SearchChecks.requireOptionalRef(evidenceRef, "evidenceRef");
        SearchChecks.requireInstant(occurredAt, "occurredAt");
    }
}
