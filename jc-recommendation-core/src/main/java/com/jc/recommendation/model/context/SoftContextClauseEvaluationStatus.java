package com.jc.recommendation.model.context;

public enum SoftContextClauseEvaluationStatus {
    MATCHED("matched"), NOT_MATCHED("not_matched"), UNKNOWN("unknown"), IGNORED("ignored");
    private final String wireValue;
    SoftContextClauseEvaluationStatus(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
