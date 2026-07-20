package com.jc.recommendation.model.context;

public enum ContextEligibilityNotApplicableReason {
    UNSUPPORTED_ENTITY_TYPE("unsupported_entity_type"),
    NO_HARD_CONTEXT_CLAUSES("no_hard_context_clauses"),
    EXPIRED_CONTEXT("expired_context");
    private final String wireValue;
    ContextEligibilityNotApplicableReason(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
