package com.jc.intelligence.contract.v1.inference;

import com.jc.intelligence.contract.support.ImmutableCollections;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import com.jc.intelligence.contract.v1.version.ModelVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.PromptVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record ModelInferenceRecordV1(
        ContractId contractVersion,
        String inferenceId,
        RunRef runId,
        InferenceStatus status,
        ModelType modelType,
        ModelVersion modelVersion,
        PromptVersion promptVersion,
        SchemaVersion toolVersion,
        SnapshotRef parameterSnapshotRef,
        SnapshotRef inputSnapshotRef,
        SnapshotRef outputSnapshotRef,
        SchemaVersion safetyPolicyVersion,
        SafetyResult safetyResult,
        ProducerBuildId producerBuildId,
        Instant startedAt,
        Instant completedAt,
        Duration latency,
        Map<String, Long> tokenOrComputeUsage,
        String failureCode,
        String fallbackCode,
        String resultHash,
        ReplayClass replayClass,
        boolean deterministicProviderGuarantee) {

    public ModelInferenceRecordV1 {
        if (!IntelligenceContractIds.MODEL_INFERENCE_RECORD.equals(contractVersion)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    "ModelInferenceRecordV1 requires model-inference-record-v1");
        }
        inferenceId = ContractChecks.requireSimpleRef(inferenceId, "inferenceId");
        java.util.Objects.requireNonNull(runId, "runId");
        java.util.Objects.requireNonNull(status, "status");
        java.util.Objects.requireNonNull(modelType, "modelType");
        java.util.Objects.requireNonNull(modelVersion, "modelVersion");
        java.util.Objects.requireNonNull(promptVersion, "promptVersion");
        if (toolVersion != null) {
            java.util.Objects.requireNonNull(toolVersion, "toolVersion");
        }
        java.util.Objects.requireNonNull(parameterSnapshotRef, "parameterSnapshotRef");
        java.util.Objects.requireNonNull(inputSnapshotRef, "inputSnapshotRef");
        java.util.Objects.requireNonNull(safetyPolicyVersion, "safetyPolicyVersion");
        java.util.Objects.requireNonNull(safetyResult, "safetyResult");
        java.util.Objects.requireNonNull(producerBuildId, "producerBuildId");
        ContractChecks.requireOrdered(startedAt, completedAt, "startedAt", "completedAt");
        latency = ContractChecks.requireNonnegative(latency, "latency");
        if (!latency.equals(Duration.between(startedAt, completedAt))) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_TIME_RANGE_INVALID,
                    "latency must equal completedAt-startedAt");
        }
        tokenOrComputeUsage = ImmutableCollections.insertionOrderedCopy(
                tokenOrComputeUsage,
                "tokenOrComputeUsage");
        for (Map.Entry<String, Long> entry : tokenOrComputeUsage.entrySet()) {
            if (entry.getValue() < 0L) {
                throw ContractChecks.invalid(
                        IntelligenceValidationErrorCode.INTELLIGENCE_INFERENCE_INVALID,
                        "token/compute usage must be nonnegative");
            }
        }
        java.util.Objects.requireNonNull(replayClass, "replayClass");
        if (replayClass == ReplayClass.EXACT_REPLAY && !deterministicProviderGuarantee) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REPLAY_CLASS_INVALID,
                    "model inference cannot claim exact_replay without an explicit deterministic provider guarantee");
        }

        switch (status) {
            case SUCCEEDED -> {
                if (outputSnapshotRef == null
                        || failureCode != null
                        || fallbackCode != null
                        || resultHash == null) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_INFERENCE_INVALID,
                            "succeeded inference requires output/result hash and no failure/fallback code");
                }
            }
            case FALLBACK -> {
                if (outputSnapshotRef == null
                        || fallbackCode == null
                        || failureCode != null
                        || resultHash == null) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_INFERENCE_INVALID,
                            "fallback inference requires output/result hash and fallbackCode only");
                }
                fallbackCode = ContractChecks.requireReasonCode(fallbackCode, "fallbackCode");
            }
            case FAILED -> {
                if (failureCode == null || fallbackCode != null || outputSnapshotRef != null) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_INFERENCE_INVALID,
                            "failed inference requires failureCode, no fallbackCode, and no output snapshot");
                }
                failureCode = ContractChecks.requireReasonCode(failureCode, "failureCode");
            }
        }
        if (resultHash != null) {
            resultHash = ContractChecks.requireHash(resultHash, "resultHash");
        }
    }
}
