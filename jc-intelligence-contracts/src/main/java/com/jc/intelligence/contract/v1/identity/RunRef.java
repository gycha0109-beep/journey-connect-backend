package com.jc.intelligence.contract.v1.identity;

import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;

public record RunRef(String value) {
    public RunRef {
        ContractChecks.requireText(value, "runRef", IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        if (value.length() > 256 || value.chars().anyMatch(Character::isWhitespace)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID,
                    "runRef contains whitespace or exceeds 256 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
