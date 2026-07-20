package com.jc.intelligence.contract.v1.inference;

import com.jc.intelligence.contract.support.WireValue;

public enum SafetyResult implements WireValue {
    PASSED("passed"),
    BLOCKED("blocked"),
    NOT_APPLICABLE("not_applicable");

    private final String wireValue;

    SafetyResult(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static SafetyResult fromWire(String value) {
        for (SafetyResult candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown safety result: " + value);
    }
}
