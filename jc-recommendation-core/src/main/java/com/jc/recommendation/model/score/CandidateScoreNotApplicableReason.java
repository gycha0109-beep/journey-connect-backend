package com.jc.recommendation.model.score;

public enum CandidateScoreNotApplicableReason {
    UNSUPPORTED_ENTITY_TYPE("unsupported_entity_type"),
    EXPIRED_CONTEXT("expired_context"),
    NO_ANCHOR_COMPONENT("no_anchor_component");
    private final String wireValue;
    CandidateScoreNotApplicableReason(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
