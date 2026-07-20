package com.jc.recommendation.model.exposure;

import java.util.List;

public record ResolveRecommendationExposureEventsResult(
        int inputCount, int resolvedCount, int duplicateCount,
        List<RecommendationExposureEventV1> resolvedEvents,
        List<RecommendationExposureDuplicateAudit> duplicateAudits
) {
    public ResolveRecommendationExposureEventsResult {
        resolvedEvents = List.copyOf(resolvedEvents);
        duplicateAudits = List.copyOf(duplicateAudits);
    }
}
