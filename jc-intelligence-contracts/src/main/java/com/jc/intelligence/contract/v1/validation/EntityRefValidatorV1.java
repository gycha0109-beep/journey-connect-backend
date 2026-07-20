package com.jc.intelligence.contract.v1.validation;

import com.jc.intelligence.contract.v1.identity.EntityRef;

public final class EntityRefValidatorV1 {
    private EntityRefValidatorV1() {
    }

    public static ValidationResultV1 validate(String value) {
        try {
            new EntityRef(value);
            return ValidationResultV1.valid();
        } catch (IntelligenceContractValidationException exception) {
            return ValidationResultV1.invalid(exception.errorCode(), "entityRef", exception.getMessage());
        }
    }
}
