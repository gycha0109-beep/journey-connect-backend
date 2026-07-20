package com.jc.recommendation.model.score;

public enum ScoreCompositionMode {
    PERSONALIZED_CONTEXTUAL("personalized_contextual"),
    CONTEXTUAL_ONLY("contextual_only"),
    INTEREST_ONLY("interest_only");
    private final String wireValue;
    ScoreCompositionMode(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
