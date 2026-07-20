package com.jc.intelligence.contract.v1.authority;

import com.jc.intelligence.contract.support.WireValue;

public enum ExposureSourceId implements WireValue {
    RECOMMENDATION_GENERAL_EXPOSURE_V1("recommendation_general_exposure_v1"),
    RECOMMENDATION_BEHAVIOR_IMPRESSION_V1("recommendation_behavior_impression_v1"),
    RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1("recommendation_p2_experiment_exposure_v1"),
    SEARCH_EXPOSURE_V1("search_exposure_v1");

    private final String wireValue;

    ExposureSourceId(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static ExposureSourceId fromWire(String value) {
        for (ExposureSourceId candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown exposure source: " + value);
    }
}
