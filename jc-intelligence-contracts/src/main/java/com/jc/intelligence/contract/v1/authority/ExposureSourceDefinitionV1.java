package com.jc.intelligence.contract.v1.authority;

public record ExposureSourceDefinitionV1(
        ExposureSourceId id,
        ExposureSourceStatus status,
        String source,
        String purpose,
        boolean p2ExperimentDenominatorAuthority,
        boolean runtimeImplemented) {
    public ExposureSourceDefinitionV1 {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(status, "status");
        source = com.jc.intelligence.contract.v1.validation.ContractChecks.requireText(
                source,
                "source",
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_REFERENCE_INVALID);
        purpose = com.jc.intelligence.contract.v1.validation.ContractChecks.requireText(
                purpose,
                "purpose",
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_REFERENCE_INVALID);
        if (id == ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1
                && !p2ExperimentDenominatorAuthority) {
            throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                    com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                            .INTELLIGENCE_REFERENCE_INVALID,
                    "P2 experiment exposure must retain denominator authority");
        }
        if (id == ExposureSourceId.SEARCH_EXPOSURE_V1 && runtimeImplemented) {
            throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                    com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                            .INTELLIGENCE_REFERENCE_INVALID,
                    "search_exposure_v1 is reserved and has no IP-1 runtime");
        }
    }
}
