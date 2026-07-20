package com.jc.recommendation.model.score;

public enum ScoreComponentName {
    CONTEXT_MATCH("context_match"),
    INTEREST_MATCH("interest_match"),
    FRESHNESS("freshness"),
    POPULARITY("popularity");

    private final String wireValue;

    ScoreComponentName(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
