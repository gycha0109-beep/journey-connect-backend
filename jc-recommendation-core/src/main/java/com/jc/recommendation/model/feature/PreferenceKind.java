package com.jc.recommendation.model.feature;

public enum PreferenceKind {
    PREFER("prefer"),
    AVOID("avoid");

    private final String wireValue;

    PreferenceKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
