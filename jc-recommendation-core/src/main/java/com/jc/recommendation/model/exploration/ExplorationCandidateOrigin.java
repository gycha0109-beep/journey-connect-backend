package com.jc.recommendation.model.exploration;

public enum ExplorationCandidateOrigin {
    PERSONALIZED("personalized"),
    EXPLORATION("exploration");

    private final String wireValue;

    ExplorationCandidateOrigin(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
