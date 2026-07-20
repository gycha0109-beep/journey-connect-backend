package com.jc.intelligence.contract.v1.validation;

import com.jc.intelligence.contract.v1.identity.IdentitySchemeId;
import com.jc.intelligence.contract.v1.identity.SubjectRef;

public final class SubjectRefValidatorV1 {
    private SubjectRefValidatorV1() {
    }

    public static ValidationResultV1 validate(IdentitySchemeId schemeId, String value) {
        try {
            new SubjectRef(schemeId, value);
            return ValidationResultV1.valid();
        } catch (IntelligenceContractValidationException exception) {
            return ValidationResultV1.invalid(exception.errorCode(), "subjectRef", exception.getMessage());
        }
    }
}
