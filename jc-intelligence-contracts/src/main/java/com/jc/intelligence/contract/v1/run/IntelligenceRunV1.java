package com.jc.intelligence.contract.v1.run;

import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import com.jc.intelligence.contract.v1.version.ModelVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.PromptVersion;
import java.time.Instant;

public record IntelligenceRunV1(
        ContractId contractVersion,
        RunRef runId,
        IntelligenceRunType runType,
        IntelligenceRunStatus status,
        String requestId,
        String correlationId,
        String domainRunMode,
        String surface,
        EntityRef entityRef,
        SubjectRef subjectRef,
        SnapshotRef inputSnapshotRef,
        SnapshotRef outputSnapshotRef,
        PolicyVersion policyVersion,
        FeatureDefinitionVersion featureDefinitionVersion,
        ModelVersion modelVersion,
        PromptVersion promptVersion,
        ProducerBuildId producerBuildId,
        Instant referenceTime,
        Instant startedAt,
        Instant completedAt,
        ReplayClass replayClass,
        ReplayEvidenceDescriptorV1 replayEvidence,
        String fallbackCode,
        String failureCode,
        ExperimentRefV1 experimentRef,
        ExposureSourceId exposureSourceRef) {

    public IntelligenceRunV1 {
        if (!IntelligenceContractIds.INTELLIGENCE_RUN.equals(contractVersion)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    "IntelligenceRunV1 requires intelligence-run-v1");
        }
        java.util.Objects.requireNonNull(runId, "runId");
        java.util.Objects.requireNonNull(runType, "runType");
        java.util.Objects.requireNonNull(status, "status");
        if (requestId != null) {
            requestId = ContractChecks.requireText(
                    requestId,
                    "requestId",
                    IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        }
        if (correlationId != null) {
            correlationId = ContractChecks.requireText(
                    correlationId,
                    "correlationId",
                    IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        }
        if (domainRunMode != null) {
            domainRunMode = ContractChecks.requireText(
                    domainRunMode,
                    "domainRunMode",
                    IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        }
        if (surface != null) {
            surface = ContractChecks.requireText(
                    surface,
                    "surface",
                    IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        }
        java.util.Objects.requireNonNull(inputSnapshotRef, "inputSnapshotRef");
        java.util.Objects.requireNonNull(producerBuildId, "producerBuildId");
        ContractChecks.requireInstant(referenceTime, "referenceTime");
        ContractChecks.requireOrdered(startedAt, completedAt, "startedAt", "completedAt");
        java.util.Objects.requireNonNull(replayClass, "replayClass");
        java.util.Objects.requireNonNull(replayEvidence, "replayEvidence");
        if (replayEvidence.replayClass() != replayClass) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REPLAY_CLASS_INVALID,
                    "replayClass and replayEvidence must agree");
        }

        switch (status) {
            case SUCCEEDED -> {
                if (outputSnapshotRef == null || fallbackCode != null || failureCode != null) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID,
                            "succeeded requires output and forbids fallback/failure codes");
                }
            }
            case FALLBACK -> {
                if (outputSnapshotRef == null || fallbackCode == null || failureCode != null) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID,
                            "fallback requires output and fallbackCode only");
                }
                fallbackCode = ContractChecks.requireReasonCode(fallbackCode, "fallbackCode");
            }
            case FAILED -> {
                if (failureCode == null || fallbackCode != null) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID,
                            "failed requires failureCode and forbids fallbackCode");
                }
                failureCode = ContractChecks.requireReasonCode(failureCode, "failureCode");
            }
        }
    }
}
