package com.jc.recommendation.model.evaluation;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import java.util.Objects;

public record RecommendationOutcomeAttributionAudit(
        String behaviorEventId, RecommendationAttributionAuditCategory category,
        String recommendationRunId, String entityId, RecommendationEntityType resolvedEntityType,
        String exposureEventId
) {
    public RecommendationOutcomeAttributionAudit {
        Objects.requireNonNull(behaviorEventId, "behaviorEventId");
        Objects.requireNonNull(category, "category");
    }
}
