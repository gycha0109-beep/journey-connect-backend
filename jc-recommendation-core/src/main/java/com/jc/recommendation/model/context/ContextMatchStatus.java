package com.jc.recommendation.model.context;

public enum ContextMatchStatus {
    SCORED("scored"), NOT_APPLICABLE("not_applicable");
    private final String wireValue;
    ContextMatchStatus(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
