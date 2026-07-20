package com.jc.intelligence.contract.v1.snapshot;

import com.jc.intelligence.contract.support.WireValue;

public enum PrivacyClass implements WireValue {
    PUBLIC("public"),
    INTERNAL("internal"),
    PSEUDONYMOUS("pseudonymous"),
    RESTRICTED("restricted");

    private final String wireValue;

    PrivacyClass(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static PrivacyClass fromWire(String value) {
        for (PrivacyClass candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown privacy class: " + value);
    }
}
