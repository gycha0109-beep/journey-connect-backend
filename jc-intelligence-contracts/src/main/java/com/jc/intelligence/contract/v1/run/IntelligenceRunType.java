package com.jc.intelligence.contract.v1.run;

import com.jc.intelligence.contract.support.WireValue;

public enum IntelligenceRunType implements WireValue {
    RECOMMENDATION("recommendation"),
    SEARCH("search"),
    CONTENT_ANALYSIS("content_analysis"),
    TRIP_PLANNING("trip_planning");

    private final String wireValue;

    IntelligenceRunType(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static IntelligenceRunType fromWire(String value) {
        for (IntelligenceRunType candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown intelligence run type: " + value);
    }
}
