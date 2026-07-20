package com.jc.recommendation.model.score;

public enum CandidateScoreStatus {
    SCORED("scored"), NOT_APPLICABLE("not_applicable"), HARD_EXCLUDED("hard_excluded");
    private final String wireValue;
    CandidateScoreStatus(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
