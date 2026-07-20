package com.jc.recommendation.model.exposure;

import java.util.Objects;

public record RecommendationExposureDuplicateAudit(
        String duplicateEventId, String duplicateIdempotencyKey, String keptEventId,
        String keptIdempotencyKey, RecommendationExposureDuplicateReason reason
) {
    public RecommendationExposureDuplicateAudit {
        Objects.requireNonNull(reason, "reason");
    }
}
