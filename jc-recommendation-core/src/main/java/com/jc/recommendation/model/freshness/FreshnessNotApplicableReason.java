package com.jc.recommendation.model.freshness;

public enum FreshnessNotApplicableReason {
    UNSUPPORTED_ENTITY_TYPE("unsupported_entity_type"),
    MISSING_FRESHNESS_TIMESTAMP("missing_freshness_timestamp");

    private final String wireValue;

    FreshnessNotApplicableReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
