package com.jc.intelligence.contract.v1.authority;

import com.jc.intelligence.contract.support.WireValue;

public enum FeatureAuthorityClass implements WireValue {
    SOURCE_FACT("source_fact"),
    OBSERVED_BEHAVIOR("observed_behavior"),
    DERIVED_AGGREGATE("derived_aggregate"),
    MODEL_INFERENCE("model_inference"),
    OPERATOR_OVERRIDE("operator_override");

    private final String wireValue;

    FeatureAuthorityClass(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static FeatureAuthorityClass fromWire(String value) {
        for (FeatureAuthorityClass candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown feature authority class: " + value);
    }
}
