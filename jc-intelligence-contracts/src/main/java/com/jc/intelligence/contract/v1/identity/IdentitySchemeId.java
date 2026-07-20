package com.jc.intelligence.contract.v1.identity;

import com.jc.intelligence.contract.support.WireValue;

public enum IdentitySchemeId implements WireValue {
    PLATFORM_SUBJECT_V1("platform_subject_v1"),
    LEGACY_USER_NUMERIC_V1("legacy_user_numeric_v1");

    private final String wireValue;

    IdentitySchemeId(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static IdentitySchemeId fromWire(String value) {
        for (IdentitySchemeId candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown identity scheme: " + value);
    }
}
