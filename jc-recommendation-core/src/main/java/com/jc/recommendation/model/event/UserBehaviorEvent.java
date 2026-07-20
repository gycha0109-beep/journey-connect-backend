package com.jc.recommendation.model.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record UserBehaviorEvent(
        String eventId,
        String idempotencyKey,
        String userId,
        String sessionId,
        EventType eventType,
        String entityId,
        String recommendationRunId,
        UserBehaviorEventMetadata metadata,
        String occurredAt
) {
    public UserBehaviorEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public Map<String, Object> payloadWithoutEventId() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idempotencyKey", idempotencyKey);
        result.put("userId", userId);
        result.put("sessionId", sessionId);
        result.put("eventType", eventType.wireValue());
        if (entityId != null) {
            result.put("entityId", entityId);
        }
        if (recommendationRunId != null) {
            result.put("recommendationRunId", recommendationRunId);
        }
        result.put("metadata", metadata.toCanonicalMap());
        result.put("occurredAt", occurredAt);
        return Collections.unmodifiableMap(result);
    }
}
