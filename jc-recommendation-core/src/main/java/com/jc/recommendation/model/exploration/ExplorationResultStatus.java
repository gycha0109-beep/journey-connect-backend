package com.jc.recommendation.model.exploration;

public enum ExplorationResultStatus {
    UNCHANGED("unchanged"),
    INSERTED("inserted");

    private final String wireValue;

    ExplorationResultStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
