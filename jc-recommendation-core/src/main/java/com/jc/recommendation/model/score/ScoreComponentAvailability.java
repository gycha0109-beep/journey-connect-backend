package com.jc.recommendation.model.score;

public enum ScoreComponentAvailability {
    SCORED("scored"), NEUTRAL_FILLED("neutral_filled"),
    STRUCTURALLY_EXCLUDED("structurally_excluded"), HARD_GATE("hard_gate");
    private final String wireValue;
    ScoreComponentAvailability(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
