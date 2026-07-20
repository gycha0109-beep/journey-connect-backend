package com.jc.intelligence.contract.v1.replay;

import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;

public record ReplayEvidenceDescriptorV1(
        ReplayClass replayClass,
        boolean deterministicPath,
        boolean immutableInputBound,
        boolean immutableOutputBound,
        boolean versionsBound,
        boolean deterministicSeedBound,
        boolean modelOrProviderInferenceUsed) {
    public ReplayEvidenceDescriptorV1 {
        java.util.Objects.requireNonNull(replayClass, "replayClass");
        if (replayClass == ReplayClass.EXACT_REPLAY
                && (!deterministicPath
                    || !immutableInputBound
                    || !immutableOutputBound
                    || !versionsBound
                    || modelOrProviderInferenceUsed)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REPLAY_CLASS_INVALID,
                    "exact_replay requires deterministic, version-bound, immutable evidence without model/provider inference");
        }
        if (replayClass == ReplayClass.SEMANTIC_REPLAY
                && !modelOrProviderInferenceUsed
                && deterministicPath
                && immutableInputBound
                && immutableOutputBound
                && versionsBound) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REPLAY_CLASS_INVALID,
                    "semantic_replay must not weaken a fully exact deterministic path without evidence");
        }
        if (replayClass == ReplayClass.EVIDENCE_REPLAY
                && (!immutableInputBound || !immutableOutputBound || !versionsBound)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REPLAY_CLASS_INVALID,
                    "evidence_replay requires reviewable input, output, and version evidence");
        }
    }

    public static ReplayEvidenceDescriptorV1 deterministicRecommendationExact(boolean seedBound) {
        return new ReplayEvidenceDescriptorV1(
                ReplayClass.EXACT_REPLAY,
                true,
                true,
                true,
                true,
                seedBound,
                false);
    }

    public static ReplayEvidenceDescriptorV1 modelEvidenceOnly() {
        return new ReplayEvidenceDescriptorV1(
                ReplayClass.EVIDENCE_REPLAY,
                false,
                true,
                true,
                true,
                false,
                true);
    }
}
