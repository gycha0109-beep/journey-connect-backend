package com.jc.intelligence.contract.v1.identity;

public record IdentitySchemeDefinitionV1(
        IdentitySchemeId id,
        String wirePrefix,
        String status,
        boolean automaticConversionAllowed) {
    public IdentitySchemeDefinitionV1 {
        java.util.Objects.requireNonNull(id, "id");
        wirePrefix = com.jc.intelligence.contract.v1.validation.ContractChecks.requireText(
                wirePrefix,
                "wirePrefix",
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_SUBJECT_REF_INVALID);
        status = com.jc.intelligence.contract.v1.validation.ContractChecks.requireText(
                status,
                "status",
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_SUBJECT_REF_INVALID);
        if (automaticConversionAllowed) {
            throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                    com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                            .INTELLIGENCE_SUBJECT_REF_INVALID,
                    "SC-1 identity schemes do not allow automatic conversion");
        }
    }
}
