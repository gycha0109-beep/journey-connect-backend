package com.jc.recommendation.model.evaluation;

import com.jc.recommendation.model.event.EventType;
import java.util.Objects;

public record ResolvedRecommendationBehaviorEvent(
        String eventId, String idempotencyKey, String userId, String sessionId, EventType eventType,
        String entityId, String recommendationRunId, String occurredAt
) {
    public ResolvedRecommendationBehaviorEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
