package com.jc.intelligence.contract.v1.validation;

import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;

public final class ReplayClassValidatorV1 {
    private ReplayClassValidatorV1() {
    }

    public static ValidationResultV1 validate(ReplayEvidenceDescriptorV1 value) {
        if (value == null) {
            return ValidationResultV1.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REPLAY_CLASS_INVALID,
                    "replayEvidence",
                    "replay evidence is required");
        }
        return ValidationResultV1.valid();
    }

    public static ValidationResultV1 validateConstruction(
            com.jc.intelligence.contract.v1.replay.ReplayClass replayClass,
            boolean deterministicPath,
            boolean immutableInputBound,
            boolean immutableOutputBound,
            boolean versionsBound,
            boolean deterministicSeedBound,
            boolean modelOrProviderInferenceUsed) {
        try {
            new ReplayEvidenceDescriptorV1(
                    replayClass,
                    deterministicPath,
                    immutableInputBound,
                    immutableOutputBound,
                    versionsBound,
                    deterministicSeedBound,
                    modelOrProviderInferenceUsed);
            return ValidationResultV1.valid();
        } catch (IntelligenceContractValidationException exception) {
            return ValidationResultV1.invalid(
                    exception.errorCode(),
                    "replayClass",
                    exception.getMessage());
        }
    }
}
