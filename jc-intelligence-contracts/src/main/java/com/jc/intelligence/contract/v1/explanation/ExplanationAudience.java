package com.jc.intelligence.contract.v1.explanation;

import com.jc.intelligence.contract.support.WireValue;

public enum ExplanationAudience implements WireValue {
    USER("user"),
    OPERATOR("operator"),
    EVALUATION("evaluation"),
    DEBUG("debug");

    private final String wireValue;

    ExplanationAudience(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static ExplanationAudience fromWire(String value) {
        for (ExplanationAudience candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown explanation audience: " + value);
    }
}
