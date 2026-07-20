package com.jc.recommendation.model.context;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.policy.ContextPolicy;
import com.jc.recommendation.policy.SourcePriorityPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CalculateContextMatchInput(
        RecommendationContext context,
        ContextEligibilityResult eligibilityResult,
        String entityId,
        RecommendationEntityType entityType,
        List<EntityFeature> entityFeatures,
        Instant referenceTime,
        ContextPolicy policy,
        SourcePriorityPolicy sourcePriorityPolicy
) {
    public CalculateContextMatchInput {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(eligibilityResult, "eligibilityResult");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        entityFeatures = List.copyOf(entityFeatures);
        Objects.requireNonNull(referenceTime, "referenceTime");
    }
}
