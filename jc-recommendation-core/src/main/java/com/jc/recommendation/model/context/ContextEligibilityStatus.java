package com.jc.recommendation.model.context;

public enum ContextEligibilityStatus {
    ELIGIBLE("eligible"), HARD_EXCLUDED("hard_excluded"), NOT_APPLICABLE("not_applicable");
    private final String wireValue;
    ContextEligibilityStatus(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
