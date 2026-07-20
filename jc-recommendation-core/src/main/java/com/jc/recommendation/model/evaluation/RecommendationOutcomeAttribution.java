package com.jc.recommendation.model.evaluation;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.event.EventType;
import java.util.Objects;

public record RecommendationOutcomeAttribution(
        String behaviorEventId, EventType behaviorEventType, String recommendationRunId,
        String exposureEventId, RecommendationEntityType entityType, String entityId,
        String exposureServedAt, String behaviorOccurredAt, long elapsedMs, long attributionWindowMs,
        Double associatedOutcomeValue, boolean isPositive, boolean isNegative, boolean isSevereReport
) {
    public RecommendationOutcomeAttribution {
        Objects.requireNonNull(behaviorEventId, "behaviorEventId");
        Objects.requireNonNull(behaviorEventType, "behaviorEventType");
        Objects.requireNonNull(recommendationRunId, "recommendationRunId");
        Objects.requireNonNull(exposureEventId, "exposureEventId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(exposureServedAt, "exposureServedAt");
        Objects.requireNonNull(behaviorOccurredAt, "behaviorOccurredAt");
    }
}
