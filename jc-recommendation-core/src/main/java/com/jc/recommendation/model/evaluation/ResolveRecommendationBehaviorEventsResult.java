package com.jc.recommendation.model.evaluation;

import java.util.List;

public record ResolveRecommendationBehaviorEventsResult(
        int inputCount, int resolvedCount, int duplicateCount,
        List<ResolvedRecommendationBehaviorEvent> resolvedEvents,
        List<RecommendationBehaviorDuplicateAudit> duplicateAudits
) {
    public ResolveRecommendationBehaviorEventsResult {
        resolvedEvents = List.copyOf(resolvedEvents);
        duplicateAudits = List.copyOf(duplicateAudits);
    }
}
