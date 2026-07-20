package com.jc.recommendation.p1.profile;

public enum P1SignalSource {
    EXPLICIT("explicit"),
    BEHAVIOR("behavior"),
    COMBINED("combined");

    private final String wireValue;

    P1SignalSource(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
