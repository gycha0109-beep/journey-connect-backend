package com.jc.intelligence.contract.v1.search.retrieval;

import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;

public record RetrievalCandidateV1(
        ContractId contractVersion,
        EntityRef entityRef,
        SearchEntityType entityType,
        String sourceId,
        RetrievalSource retrievalSource,
        Double retrievalScore,
        Integer sourceRank,
        Instant retrievedAt,
        SnapshotRef sourceSnapshotRef,
        SearchEligibilityState eligibilityState,
        SearchVisibilityState visibilityState,
        String candidateMetadataRef,
        SchemaVersion retrievalStrategyVersion) {
    public RetrievalCandidateV1 {
        SearchVersionValidatorV1.requireContract(contractVersion, SearchContractIds.SEARCH_RETRIEVAL_RANKING);
        SearchChecks.requireNonNull(entityRef, "entityRef");
        SearchChecks.requireNonNull(entityType, "entityType");
        sourceId = SearchChecks.requireNoWhitespace(sourceId, "sourceId", 128);
        SearchChecks.requireNonNull(retrievalSource, "retrievalSource");
        retrievalScore = SearchChecks.requireFinite(retrievalScore, "retrievalScore");
        if (sourceRank != null && sourceRank.intValue() < 1) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_RANK_INVALID, "sourceRank must be 1-based when present");
        }
        SearchChecks.requireInstant(retrievedAt, "retrievedAt");
        SearchChecks.requireNonNull(sourceSnapshotRef, "sourceSnapshotRef");
        SearchChecks.requireNonNull(eligibilityState, "eligibilityState");
        SearchChecks.requireNonNull(visibilityState, "visibilityState");
        if (candidateMetadataRef != null) {
            candidateMetadataRef = SearchChecks.requireOptionalRef(candidateMetadataRef, "candidateMetadataRef");
        }
        SearchChecks.requireNonNull(retrievalStrategyVersion, "retrievalStrategyVersion");
        if (!entityType.wireValue().equals(entityRef.entityType()) || !sourceId.equals(entityRef.sourceId())) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                    "entity type/source ID must match entityRef");
        }
    }
}
