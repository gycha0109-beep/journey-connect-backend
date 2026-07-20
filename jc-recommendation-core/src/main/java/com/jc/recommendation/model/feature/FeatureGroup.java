package com.jc.recommendation.model.feature;

public enum FeatureGroup {
    REGION("region"),
    THEME("theme"),
    PACE("pace"),
    BUDGET("budget"),
    COMPANION("companion"),
    ENVIRONMENT("environment"),
    TRANSPORT("transport"),
    TIME("time"),
    MOOD("mood"),
    ACTIVITY("activity");

    private final String wireValue;

    FeatureGroup(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
