package com.jc.recommendation.model.evaluation;

import java.util.Objects;

public record RecommendationBehaviorDuplicateAudit(
        String duplicateEventId, String duplicateIdempotencyKey, String keptEventId,
        String keptIdempotencyKey, RecommendationBehaviorDuplicateReason reason
) {
    public RecommendationBehaviorDuplicateAudit { Objects.requireNonNull(reason, "reason"); }
}
