package com.jc.recommendation.model.evaluation;

public enum RecommendationAttributionAuditCategory {
    MISSING_RECOMMENDATION_RUN("missing_recommendation_run"), ANONYMOUS_USER("anonymous_user"),
    RUN_USER_SESSION_MISMATCH("run_user_session_mismatch"), UNSUPPORTED_EVENT_TYPE("unsupported_event_type"),
    MISSING_ENTITY_ID("missing_entity_id"), UNMATCHED_ENTITY("unmatched_entity"),
    AMBIGUOUS_ENTITY_IDENTITY("ambiguous_entity_identity"), AFTER_CUTOFF("after_cutoff"),
    BEFORE_EXPOSURE("before_exposure"), OUTSIDE_ATTRIBUTION_WINDOW("outside_attribution_window"),
    ATTRIBUTED("attributed");
    private final String wireValue;
    RecommendationAttributionAuditCategory(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
