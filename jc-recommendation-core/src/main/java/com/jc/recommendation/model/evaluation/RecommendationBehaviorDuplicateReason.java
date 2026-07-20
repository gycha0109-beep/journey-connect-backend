package com.jc.recommendation.model.evaluation;

public enum RecommendationBehaviorDuplicateReason {
    DUPLICATE_IDEMPOTENCY_KEY("duplicate_idempotency_key"),
    DUPLICATE_EVENT_ID("duplicate_event_id"),
    DUPLICATE_IDEMPOTENCY_KEY_AND_EVENT_ID("duplicate_idempotency_key_and_event_id");
    private final String wireValue;
    RecommendationBehaviorDuplicateReason(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
