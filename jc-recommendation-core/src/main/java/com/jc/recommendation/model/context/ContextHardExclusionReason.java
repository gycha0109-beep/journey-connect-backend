package com.jc.recommendation.model.context;

public enum ContextHardExclusionReason {
    MISSING_REQUIRED_CLAUSE("missing_required_clause"),
    MATCHED_EXCLUDED_CLAUSE("matched_excluded_clause");
    private final String wireValue;
    ContextHardExclusionReason(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
