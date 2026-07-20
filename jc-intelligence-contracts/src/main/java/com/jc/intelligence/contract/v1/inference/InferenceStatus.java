package com.jc.intelligence.contract.v1.inference;

import com.jc.intelligence.contract.support.WireValue;

public enum InferenceStatus implements WireValue {
    SUCCEEDED("succeeded"),
    FALLBACK("fallback"),
    FAILED("failed");

    private final String wireValue;

    InferenceStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static InferenceStatus fromWire(String value) {
        for (InferenceStatus candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown inference status: " + value);
    }
}
