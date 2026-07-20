package com.jc.recommendation.model.interest;

import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.ExplicitPreference;
import com.jc.recommendation.policy.InterestMatchPolicy;
import com.jc.recommendation.policy.SourcePriorityPolicy;

import java.util.List;
import java.util.Objects;

public record CalculateInterestMatchInput(
        String userId,
        String entityId,
        List<ExplicitPreference> explicitPreferences,
        List<UserInterestSignal> inferredSignals,
        List<EntityFeature> entityFeatures,
        InterestMatchPolicy policy,
        SourcePriorityPolicy sourcePriorityPolicy
) {
    public CalculateInterestMatchInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(entityId, "entityId");
        explicitPreferences = List.copyOf(explicitPreferences);
        inferredSignals = List.copyOf(inferredSignals);
        entityFeatures = List.copyOf(entityFeatures);
    }

    public CalculateInterestMatchInput(
            String userId,
            String entityId,
            List<ExplicitPreference> explicitPreferences,
            List<UserInterestSignal> inferredSignals,
            List<EntityFeature> entityFeatures
    ) {
        this(userId, entityId, explicitPreferences, inferredSignals, entityFeatures, null, null);
    }
}
