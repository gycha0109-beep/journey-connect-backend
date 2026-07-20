package com.jc.intelligence.contract.v1.validation;

import com.jc.intelligence.contract.v1.run.IntelligenceRunV1;

public final class IntelligenceRunValidatorV1 {
    private IntelligenceRunValidatorV1() {
    }

    public static ValidationResultV1 validate(IntelligenceRunV1 value) {
        if (value == null) {
            return ValidationResultV1.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID,
                    "run",
                    "run is required");
        }
        return ValidationResultV1.valid();
    }
}
