package com.jc.recommendation.model.interest;

public enum InterestMatchStatus {
    SCORED("scored"),
    NOT_APPLICABLE("not_applicable"),
    HARD_EXCLUDED("hard_excluded");

    private final String wireValue;

    InterestMatchStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
