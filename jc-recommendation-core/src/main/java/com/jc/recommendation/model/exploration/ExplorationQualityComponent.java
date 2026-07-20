package com.jc.recommendation.model.exploration;

public enum ExplorationQualityComponent {
    FRESHNESS("freshness"),
    POPULARITY("popularity");

    private final String wireValue;

    ExplorationQualityComponent(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
