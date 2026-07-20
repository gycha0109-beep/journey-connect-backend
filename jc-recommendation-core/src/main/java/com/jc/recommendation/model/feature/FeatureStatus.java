package com.jc.recommendation.model.feature;

public enum FeatureStatus {
    ACTIVE("active"),
    DEPRECATED("deprecated"),
    MERGED("merged");

    private final String wireValue;

    FeatureStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
