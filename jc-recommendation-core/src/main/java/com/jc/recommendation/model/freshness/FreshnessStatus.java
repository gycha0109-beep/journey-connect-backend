package com.jc.recommendation.model.freshness;

public enum FreshnessStatus {
    SCORED("scored"),
    NOT_APPLICABLE("not_applicable");

    private final String wireValue;

    FreshnessStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
