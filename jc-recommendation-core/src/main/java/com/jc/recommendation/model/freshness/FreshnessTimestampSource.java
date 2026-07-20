package com.jc.recommendation.model.freshness;

public enum FreshnessTimestampSource {
    PUBLISHED_AT("published_at"),
    CREATED_AT("created_at"),
    MEANINGFUL_UPDATED_AT("meaningful_updated_at");

    private final String wireValue;

    FreshnessTimestampSource(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
