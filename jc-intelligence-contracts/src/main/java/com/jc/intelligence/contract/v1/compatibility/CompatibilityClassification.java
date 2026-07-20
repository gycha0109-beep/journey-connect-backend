package com.jc.intelligence.contract.v1.compatibility;

import com.jc.intelligence.contract.support.WireValue;

public enum CompatibilityClassification implements WireValue {
    EXACT_COMPATIBLE("exact_compatible"),
    ADAPTER_COMPATIBLE("adapter_compatible"),
    FUTURE_VERSION_MIGRATION_REQUIRED("future_version_migration_required"),
    INTENTIONALLY_DOMAIN_SPECIFIC("intentionally_domain_specific");

    private final String wireValue;

    CompatibilityClassification(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static CompatibilityClassification fromWire(String value) {
        for (CompatibilityClassification candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown compatibility classification: " + value);
    }
}
