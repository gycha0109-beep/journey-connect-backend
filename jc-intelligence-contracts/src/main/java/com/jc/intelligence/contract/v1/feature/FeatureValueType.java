package com.jc.intelligence.contract.v1.feature;

import com.jc.intelligence.contract.support.WireValue;

public enum FeatureValueType implements WireValue {
    STRING("string"),
    BOOLEAN("boolean"),
    LONG("long"),
    DOUBLE("double"),
    STRING_LIST("string_list");

    private final String wireValue;

    FeatureValueType(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static FeatureValueType fromWire(String value) {
        for (FeatureValueType candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown feature value type: " + value);
    }
}
