package com.jc.recommendation.model.feature;

public enum FeatureSource {
    EXPLICIT("explicit"),
    BEHAVIOR("behavior"),
    AI("ai"),
    SYSTEM("system"),
    ADMIN("admin");

    private final String wireValue;

    FeatureSource(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
