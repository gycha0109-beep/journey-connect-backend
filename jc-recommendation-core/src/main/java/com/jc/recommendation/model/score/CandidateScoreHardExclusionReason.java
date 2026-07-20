package com.jc.recommendation.model.score;

public enum CandidateScoreHardExclusionReason {
    CONTEXT_HARD_EXCLUSION("context_hard_exclusion"),
    INTEREST_HARD_EXCLUSION("interest_hard_exclusion"),
    MULTIPLE_HARD_EXCLUSIONS("multiple_hard_exclusions");
    private final String wireValue;
    CandidateScoreHardExclusionReason(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
