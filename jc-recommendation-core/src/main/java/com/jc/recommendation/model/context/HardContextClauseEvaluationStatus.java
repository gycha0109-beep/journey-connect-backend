package com.jc.recommendation.model.context;

public enum HardContextClauseEvaluationStatus {
    MATCHED("matched"), NOT_MATCHED("not_matched"), IGNORED("ignored");
    private final String wireValue;
    HardContextClauseEvaluationStatus(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
