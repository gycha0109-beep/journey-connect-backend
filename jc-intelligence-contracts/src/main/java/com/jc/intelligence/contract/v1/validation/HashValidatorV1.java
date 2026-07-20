package com.jc.intelligence.contract.v1.validation;

public final class HashValidatorV1 {
    private HashValidatorV1() {
    }

    public static ValidationResultV1 validateSha256(String value, String field) {
        try {
            ContractChecks.requireHash(value, field);
            return ValidationResultV1.valid();
        } catch (IntelligenceContractValidationException exception) {
            return ValidationResultV1.invalid(exception.errorCode(), field, exception.getMessage());
        }
    }
}
