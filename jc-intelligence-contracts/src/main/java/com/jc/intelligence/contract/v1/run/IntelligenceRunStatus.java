package com.jc.intelligence.contract.v1.run;

import com.jc.intelligence.contract.support.WireValue;

public enum IntelligenceRunStatus implements WireValue {
    SUCCEEDED("succeeded"),
    FALLBACK("fallback"),
    FAILED("failed");

    private final String wireValue;

    IntelligenceRunStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static IntelligenceRunStatus fromWire(String value) {
        for (IntelligenceRunStatus candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown intelligence run status: " + value);
    }
}
