package com.jc.intelligence.contract.v1.validation;

import com.jc.intelligence.contract.v1.version.ContractId;

public final class ContractIdValidatorV1 {
    private ContractIdValidatorV1() {
    }

    public static ValidationResultV1 validate(String value) {
        try {
            new ContractId(value);
            return ValidationResultV1.valid();
        } catch (IntelligenceContractValidationException exception) {
            return ValidationResultV1.invalid(exception.errorCode(), "contractId", exception.getMessage());
        }
    }
}
