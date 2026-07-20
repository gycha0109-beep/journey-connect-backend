package com.jc.intelligence.contract.v1.search.run;

import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.run.IntelligenceRunType;
import com.jc.intelligence.contract.v1.run.IntelligenceRunV1;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchFailureCode;
import com.jc.intelligence.contract.v1.search.SearchFallbackCode;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;

public record SearchRunV1(
        ContractId contractVersion,
        RunRef runId,
        IntelligenceRunStatus status,
        String requestId,
        String correlationId,
        SubjectRef subjectRef,
        String sessionRef,
        SearchSurface surface,
        SearchEntityScope entityScope,
        SnapshotRef inputSnapshotRef,
        SnapshotRef candidateSnapshotRef,
        SnapshotRef outputSnapshotRef,
        SchemaVersion queryNormalizationVersion,
        SchemaVersion retrievalStrategyVersion,
        PolicyVersion rankingPolicyVersion,
        FeatureDefinitionVersion featureDefinitionVersion,
        Instant referenceTime,
        Instant startedAt,
        Instant completedAt,
        ProducerBuildId producerBuildId,
        ReplayClass replayClass,
        ReplayEvidenceDescriptorV1 replayEvidence,
        SearchFallbackCode fallbackCode,
        SearchFailureCode failureCode) {
    public SearchRunV1 {
        SearchVersionValidatorV1.requireContract(contractVersion, SearchContractIds.SEARCH_DOMAIN);
        SearchChecks.requireNonNull(runId, "runId");
        SearchChecks.requireNonNull(status, "status");
        if (requestId != null) requestId = SearchChecks.requireOpaqueId(requestId, "requestId");
        if (correlationId != null) correlationId = SearchChecks.requireOpaqueId(correlationId, "correlationId");
        if (sessionRef != null) sessionRef = SearchChecks.requireOpaqueId(sessionRef, "sessionRef");
        SearchChecks.requireNonNull(surface, "surface");
        SearchChecks.requireNonNull(entityScope, "entityScope");
        SearchChecks.requireNonNull(inputSnapshotRef, "inputSnapshotRef");
        SearchVersionValidatorV1.requireQueryNormalization(queryNormalizationVersion);
        SearchChecks.requireNonNull(retrievalStrategyVersion, "retrievalStrategyVersion");
        SearchVersionValidatorV1.requirePolicy(rankingPolicyVersion, "rankingPolicyVersion");
        SearchChecks.requireNonNull(featureDefinitionVersion, "featureDefinitionVersion");
        SearchChecks.requireInstant(referenceTime, "referenceTime");
        SearchChecks.requireOrdered(startedAt, completedAt, "startedAt", "completedAt");
        SearchChecks.requireNonNull(producerBuildId, "producerBuildId");
        SearchChecks.requireNonNull(replayClass, "replayClass");
        SearchChecks.requireNonNull(replayEvidence, "replayEvidence");
        if (replayClass != replayEvidence.replayClass()) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_REPLAY_CLASS_INVALID,
                    "replayClass and replayEvidence must agree");
        }
        switch (status) {
            case SUCCEEDED -> {
                if (candidateSnapshotRef == null || outputSnapshotRef == null
                        || fallbackCode != null || failureCode != null) {
                    throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_RUN_STATUS_INVALID,
                            "succeeded search run requires candidate/output snapshots and no codes");
                }
            }
            case FALLBACK -> {
                if (candidateSnapshotRef == null || outputSnapshotRef == null
                        || fallbackCode == null || failureCode != null) {
                    throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_RUN_STATUS_INVALID,
                            "fallback search run requires candidate/output snapshots and fallbackCode only");
                }
            }
            case FAILED -> {
                if (outputSnapshotRef != null || fallbackCode != null || failureCode == null) {
                    throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_RUN_STATUS_INVALID,
                            "failed search run requires failureCode and no output/fallback");
                }
            }
        }
    }

    public IntelligenceRunV1 toIntelligenceRun() {
        return new IntelligenceRunV1(
                IntelligenceContractIds.INTELLIGENCE_RUN,
                runId,
                IntelligenceRunType.SEARCH,
                status,
                requestId,
                correlationId,
                "snapshot_bound",
                surface.wireValue(),
                null,
                subjectRef,
                inputSnapshotRef,
                outputSnapshotRef,
                rankingPolicyVersion,
                featureDefinitionVersion,
                null,
                null,
                producerBuildId,
                referenceTime,
                startedAt,
                completedAt,
                replayClass,
                replayEvidence,
                fallbackCode == null ? null : fallbackCode.name(),
                failureCode == null ? null : failureCode.name(),
                null,
                null);
    }

    public ExposureSourceId reservedExposureSourceId() {
        return ExposureSourceId.SEARCH_EXPOSURE_V1;
    }
}
