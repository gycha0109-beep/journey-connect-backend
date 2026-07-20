package com.jc.intelligence.contract.v1.validation;

public final class VersionValidatorV1 {
    private VersionValidatorV1() {
    }

    public static ValidationResultV1 validate(String value, String field) {
        try {
            ContractChecks.requireVersion(value, field);
            return ValidationResultV1.valid();
        } catch (IntelligenceContractValidationException exception) {
            return ValidationResultV1.invalid(exception.errorCode(), field, exception.getMessage());
        }
    }
}
