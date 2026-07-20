package com.jc.recommendation.model.ranking;

public enum RankingResultStatus {
    RANKED("ranked"),
    EMPTY("empty");

    private final String wireValue;

    RankingResultStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
