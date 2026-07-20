package com.jc.recommendation.scoring;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.policy.ScoreCompositionPolicy;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScoreCompositionContracts {
    public static final List<ScoreComponentName> COMPONENTS = List.of(
            ScoreComponentName.CONTEXT_MATCH,
            ScoreComponentName.INTEREST_MATCH,
            ScoreComponentName.FRESHNESS,
            ScoreComponentName.POPULARITY
    );
    public static final List<RecommendationEntityType> ELIGIBLE_ENTITIES = List.of(
            RecommendationEntityType.POST,
            RecommendationEntityType.JOURNEY,
            RecommendationEntityType.PLACE,
            RecommendationEntityType.CREW
    );
    private static final double EPSILON = 1e-12;

    private ScoreCompositionContracts() {
    }

    public static void validatePolicy(ScoreCompositionPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        requireNonBlank(policy.policyVersion(), "policy.policyVersion");
        Objects.requireNonNull(policy.effectiveFrom(), "policy.effectiveFrom");
        requireExact(policy.eligibleEntityTypes(), ELIGIBLE_ENTITIES, "policy.eligibleEntityTypes");
        requireExact(policy.componentOrder(), COMPONENTS, "policy.componentOrder");
        requireExactKeys(policy.globalBaseWeights(), COMPONENTS, "policy.globalBaseWeights");
        double sum = 0.0;
        for (ScoreComponentName component : COMPONENTS) {
            Double weight = policy.globalBaseWeights().get(component);
            if (weight == null || !Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("weight." + component.wireValue() + " must be finite in range");
            }
            sum += weight;
        }
        if (Math.abs(sum - 1.0) > EPSILON) {
            throw new IllegalArgumentException("global weights must sum to 1");
        }
        if (policy.globalBaseWeights().get(ScoreComponentName.CONTEXT_MATCH) <= 0.0
                || policy.globalBaseWeights().get(ScoreComponentName.INTEREST_MATCH) <= 0.0) {
            throw new IllegalArgumentException("anchor weights must be positive");
        }
        requireExactKeys(policy.entityComponentEligibility(), ELIGIBLE_ENTITIES,
                "policy.entityComponentEligibility");
        requireExact(policy.entityComponentEligibility().get(RecommendationEntityType.POST), COMPONENTS,
                "profile.post");
        requireExact(policy.entityComponentEligibility().get(RecommendationEntityType.JOURNEY), COMPONENTS,
                "profile.journey");
        List<ScoreComponentName> anchorOnly = List.of(
                ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH);
        requireExact(policy.entityComponentEligibility().get(RecommendationEntityType.PLACE), anchorOnly,
                "profile.place");
        requireExact(policy.entityComponentEligibility().get(RecommendationEntityType.CREW), anchorOnly,
                "profile.crew");
        requireExact(policy.anchorComponents(), anchorOnly, "policy.anchorComponents");
        ScoreComponentPolicyVersions versions = policy.expectedComponentPolicyVersions();
        requireNonBlank(versions.contextMatch(), "version.context_match");
        requireNonBlank(versions.interestMatch(), "version.interest_match");
        requireNonBlank(versions.freshness(), "version.freshness");
        requireNonBlank(versions.popularity(), "version.popularity");
        requireFiniteRange(policy.neutralPrior(), 0.0, 1.0, "policy.neutralPrior");
        if (Double.compare(policy.scoreMinimum(), 0.0) != 0 || Double.compare(policy.scoreMaximum(), 1.0) != 0) {
            throw new IllegalArgumentException("policy score range must be 0..1");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be nonblank");
        }
    }

    private static void requireFiniteRange(double value, double min, double max, String label) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(label + " must be finite in range");
        }
    }

    private static <T> void requireExact(List<T> actual, List<T> expected, String label) {
        if (actual == null || !actual.equals(expected)) {
            throw new IllegalArgumentException(label + " must match the required ordered values");
        }
    }

    private static <K, V> void requireExactKeys(Map<K, V> actual, List<K> expected, String label) {
        if (actual == null || actual.size() != expected.size() || !actual.keySet().containsAll(expected)) {
            throw new IllegalArgumentException(label + " must contain exact keys");
        }
    }
}
