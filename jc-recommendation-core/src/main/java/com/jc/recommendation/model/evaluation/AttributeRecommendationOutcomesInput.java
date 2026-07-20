package com.jc.recommendation.model.evaluation;

import com.jc.recommendation.model.exposure.ResolveRecommendationExposureEventsResult;
import java.util.Objects;

public record AttributeRecommendationOutcomesInput(
        String caseId, ResolveRecommendationExposureEventsResult exposureResult,
        ResolveRecommendationBehaviorEventsResult behaviorResult, String evaluationCutoffAt
) {
    public AttributeRecommendationOutcomesInput {
        Objects.requireNonNull(exposureResult, "exposureResult");
        Objects.requireNonNull(behaviorResult, "behaviorResult");
    }
}
