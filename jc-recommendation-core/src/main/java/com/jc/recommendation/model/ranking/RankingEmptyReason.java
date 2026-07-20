package com.jc.recommendation.model.ranking;

public enum RankingEmptyReason {
    NO_SCORED_CANDIDATES("no_scored_candidates");

    private final String wireValue;

    RankingEmptyReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
