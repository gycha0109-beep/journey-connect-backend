package com.jc.recommendation.p1.policy;

import com.jc.recommendation.model.context.ContextSurface;
import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.diversity.DiversityExposureCaps;
import com.jc.recommendation.p1.profile.BehaviorProfilePolicies;
import com.jc.recommendation.p1.profile.P1FeatureVocabulary;
import com.jc.recommendation.p1.profile.UserProfileSegment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class P1PolicySelector {
    private static final Instant EFFECTIVE_FROM = Instant.parse("2026-07-18T15:00:00Z");
    private static final List<DiversityDimension> RELAXATION_ORDER = List.of(
            DiversityDimension.THEME,
            DiversityDimension.REGION,
            DiversityDimension.AUTHOR,
            DiversityDimension.DUPLICATE_GROUP);

    public P1PolicySelection select(P1PolicySelectorInput input) {
        if (input.experimentAssignment() != P1ExperimentAssignment.TREATMENT) {
            throw new IllegalArgumentException("P1 selector only creates treatment policy bundles");
        }
        P1ScorePolicy scorePolicy = scorePolicy(input.segment());
        P1DiversityPolicy diversityPolicy = diversityPolicy(input.surface());
        List<String> reasons = new ArrayList<>();
        reasons.add("segment:" + input.segment().wireValue());
        reasons.add("surface:" + input.surface().wireValue());
        reasons.add(input.sessionContext().returningSession() ? "session:returning" : "session:new");
        reasons.add("experiment:treatment");
        P1PolicyBundle bundle = new P1PolicyBundle(
                "p1-policy-bundle-v1:" + input.surface().wireValue() + ":" + input.segment().wireValue(),
                input.segment(),
                input.surface(),
                BehaviorProfilePolicies.V1.policyVersion(),
                P1FeatureVocabulary.VERSION,
                "retrieval-policy-v2",
                "low-exposure-policy-v2",
                "exploration-policy-v2",
                scorePolicy,
                diversityPolicy);
        return new P1PolicySelection(input.experimentAssignment(), bundle, reasons);
    }

    private static P1ScorePolicy scorePolicy(UserProfileSegment segment) {
        return switch (segment) {
            case EMPTY -> score("ranking-policy-v2-empty", 0.30d, 0.10d, 0.35d, 0.25d, 14.0d, 0.65d, 0.10d, 3);
            case EXPLICIT_ONLY -> score("ranking-policy-v2-explicit", 0.25d, 0.45d, 0.20d, 0.10d, 21.0d, 0.70d, 0.08d, 3);
            case EMERGING -> score("ranking-policy-v2-emerging", 0.25d, 0.40d, 0.20d, 0.15d, 21.0d, 0.70d, 0.08d, 3);
            case ESTABLISHED -> score("ranking-policy-v2-established", 0.20d, 0.55d, 0.15d, 0.10d, 30.0d, 0.75d, 0.06d, 2);
        };
    }

    private static P1ScorePolicy score(
            String version,
            double contextWeight,
            double interestWeight,
            double freshnessWeight,
            double popularityWeight,
            double freshnessHalfLifeDays,
            double compressionExponent,
            double lowExposureBoost,
            int lowExposureThreshold) {
        return new P1ScorePolicy(
                version,
                EFFECTIVE_FROM,
                contextWeight,
                interestWeight,
                freshnessWeight,
                popularityWeight,
                freshnessHalfLifeDays,
                compressionExponent,
                lowExposureBoost,
                lowExposureThreshold,
                0.5d);
    }

    private static P1DiversityPolicy diversityPolicy(ContextSurface surface) {
        return switch (surface) {
            case HOME_FEED -> diversity("diversity-policy-home-v2", 10, 8, 8, new DiversityExposureCaps(1, 2, 4, 3));
            case SEARCH_RESULT -> diversity("diversity-policy-search-v2", 12, 5, 5, new DiversityExposureCaps(1, 3, 6, 4));
            case JOURNEY_RECOMMENDATION -> diversity("diversity-policy-journey-v2", 10, 7, 7, new DiversityExposureCaps(1, 2, 4, 2));
            case PLACE_RECOMMENDATION -> diversity("diversity-policy-place-v2", 8, 4, 4, new DiversityExposureCaps(1, 3, 4, 3));
            case CREW_RECOMMENDATION -> diversity("diversity-policy-crew-v2", 8, 4, 4, new DiversityExposureCaps(1, 2, 4, 3));
        };
    }

    private static P1DiversityPolicy diversity(
            String version,
            int window,
            int maxPromotion,
            int maxDemotion,
            DiversityExposureCaps caps) {
        return new P1DiversityPolicy(
                version,
                EFFECTIVE_FROM,
                window,
                maxPromotion,
                maxDemotion,
                caps,
                RELAXATION_ORDER);
    }
}
