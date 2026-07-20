package com.jc.recommendation.policy;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ScoreCompositionPolicy(
        String policyVersion,
        Instant effectiveFrom,
        List<RecommendationEntityType> eligibleEntityTypes,
        List<ScoreComponentName> componentOrder,
        Map<ScoreComponentName, Double> globalBaseWeights,
        Map<RecommendationEntityType, List<ScoreComponentName>> entityComponentEligibility,
        List<ScoreComponentName> anchorComponents,
        ScoreComponentPolicyVersions expectedComponentPolicyVersions,
        double neutralPrior,
        double scoreMinimum,
        double scoreMaximum
) implements VersionedPolicy {
    public ScoreCompositionPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        eligibleEntityTypes = List.copyOf(eligibleEntityTypes);
        componentOrder = List.copyOf(componentOrder);
        globalBaseWeights = Collections.unmodifiableMap(new LinkedHashMap<>(globalBaseWeights));
        Map<RecommendationEntityType, List<ScoreComponentName>> eligibilityCopy = new LinkedHashMap<>();
        entityComponentEligibility.forEach((entityType, components) -> eligibilityCopy.put(entityType, List.copyOf(components)));
        entityComponentEligibility = Collections.unmodifiableMap(eligibilityCopy);
        anchorComponents = List.copyOf(anchorComponents);
        Objects.requireNonNull(expectedComponentPolicyVersions, "expectedComponentPolicyVersions");
    }
}
