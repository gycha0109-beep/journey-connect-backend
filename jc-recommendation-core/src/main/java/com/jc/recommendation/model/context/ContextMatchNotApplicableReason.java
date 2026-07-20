package com.jc.recommendation.model.context;

public enum ContextMatchNotApplicableReason {
    UNSUPPORTED_ENTITY_TYPE("unsupported_entity_type"),
    EXPIRED_CONTEXT("expired_context"),
    HARD_CONTEXT_NOT_ELIGIBLE("hard_context_not_eligible"),
    NO_SOFT_CONTEXT_CLAUSES("no_soft_context_clauses"),
    NO_OBSERVABLE_CONTEXT_GROUPS("no_observable_context_groups");
    private final String wireValue;
    ContextMatchNotApplicableReason(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
