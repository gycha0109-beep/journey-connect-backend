package com.jc.recommendation.p1.policy;

public enum P1ExperimentAssignment {
    BASELINE("baseline"),
    TREATMENT("treatment");

    private final String wireValue;

    P1ExperimentAssignment(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
