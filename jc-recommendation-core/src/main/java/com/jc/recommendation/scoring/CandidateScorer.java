package com.jc.recommendation.scoring;

import com.jc.recommendation.model.context.ContextEligibilityNotApplicableReason;
import com.jc.recommendation.model.context.ContextEligibilityResult;
import com.jc.recommendation.model.context.ContextEligibilityStatus;
import com.jc.recommendation.model.context.ContextMatchNotApplicableReason;
import com.jc.recommendation.model.context.ContextMatchResult;
import com.jc.recommendation.model.context.ContextMatchStatus;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.freshness.FreshnessNotApplicableReason;
import com.jc.recommendation.model.freshness.FreshnessResult;
import com.jc.recommendation.model.freshness.FreshnessStatus;
import com.jc.recommendation.model.interest.InterestMatchResult;
import com.jc.recommendation.model.interest.InterestMatchStatus;
import com.jc.recommendation.model.popularity.PopularityNotApplicableReason;
import com.jc.recommendation.model.popularity.PopularityResult;
import com.jc.recommendation.model.popularity.PopularityStatus;
import com.jc.recommendation.model.score.CandidateScoreHardExclusionReason;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreCandidateInput;
import com.jc.recommendation.model.score.ScoreComponentAvailability;
import com.jc.recommendation.model.score.ScoreComponentBreakdown;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.policy.ScoreCompositionPolicy;
import com.jc.recommendation.policy.ScoringPolicies;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CandidateScorer {
    private static final double EPSILON = 1e-12;

    private record ComponentData(String status, String reason, String version, Double raw) {
    }

    public CandidateScoreResult score(ScoreCandidateInput input) {
        requireNonBlank(input.userId(), "userId");
        requireNonBlank(input.contextId(), "contextId");
        requireNonBlank(input.entityId(), "entityId");
        ScoreCompositionPolicy policy = input.policy() == null ? ScoringPolicies.SCORE_COMPOSITION_V1 : input.policy();
        ScoreCompositionContracts.validatePolicy(policy);

        validateInterest(input.interestMatchResult());
        validateEligibility(input.contextEligibilityResult());
        validateContextMatch(input.contextMatchResult());
        validateFreshness(input.freshnessResult());
        validatePopularity(input.popularityResult());

        if (!input.interestMatchResult().userId().equals(input.userId())) {
            throw new IllegalArgumentException("userId mismatch");
        }
        checkEntityId("interest", input.interestMatchResult().entityId(), input.entityId());
        checkEntityId("eligibility", input.contextEligibilityResult().entityId(), input.entityId());
        checkEntityId("context", input.contextMatchResult().entityId(), input.entityId());
        checkEntityId("freshness", input.freshnessResult().entityId(), input.entityId());
        checkEntityId("popularity", input.popularityResult().entityId(), input.entityId());
        checkEntityType("eligibility", input.contextEligibilityResult().entityType(), input.entityType());
        checkEntityType("context", input.contextMatchResult().entityType(), input.entityType());
        checkEntityType("freshness", input.freshnessResult().entityType(), input.entityType());
        checkEntityType("popularity", input.popularityResult().entityType(), input.entityType());
        if (!input.contextEligibilityResult().contextId().equals(input.contextId())
                || !input.contextMatchResult().contextId().equals(input.contextId())) {
            throw new IllegalArgumentException("contextId mismatch");
        }
        var expected = policy.expectedComponentPolicyVersions();
        if (!input.interestMatchResult().policyVersion().equals(expected.interestMatch())
                || !input.contextEligibilityResult().policyVersion().equals(expected.contextMatch())
                || !input.contextMatchResult().policyVersion().equals(expected.contextMatch())
                || !input.freshnessResult().policyVersion().equals(expected.freshness())
                || !input.popularityResult().policyVersion().equals(expected.popularity())) {
            throw new IllegalArgumentException("component policyVersion mismatch");
        }

        validateContextConsistency(input.contextEligibilityResult(), input.contextMatchResult());
        validateEntityComponentContract(input.entityType(), input.freshnessResult(), input.popularityResult());
        boolean supported = policy.eligibleEntityTypes().contains(input.entityType());
        if (supported
                && input.contextEligibilityResult().status() == ContextEligibilityStatus.NOT_APPLICABLE
                && input.contextEligibilityResult().notApplicableReason()
                == ContextEligibilityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
            throw new IllegalArgumentException("supported entity cannot be unsupported in Context");
        }
        boolean expired = input.contextEligibilityResult().status() == ContextEligibilityStatus.NOT_APPLICABLE
                && input.contextEligibilityResult().notApplicableReason()
                == ContextEligibilityNotApplicableReason.EXPIRED_CONTEXT;

        Map<ScoreComponentName, Double> weights = effectiveWeights(policy, input.entityType());
        Map<ScoreComponentName, ScoreComponentAvailability> availability = new EnumMap<>(ScoreComponentName.class);
        for (ScoreComponentName component : ScoreCompositionContracts.COMPONENTS) {
            availability.put(component, baseAvailability(component, input, supported, expired));
        }
        List<ScoreComponentName> anchors = ScoreCompositionContracts.COMPONENTS.stream()
                .filter(component -> (component == ScoreComponentName.CONTEXT_MATCH
                        || component == ScoreComponentName.INTEREST_MATCH)
                        && availability.get(component) == ScoreComponentAvailability.SCORED)
                .toList();
        List<ScoreComponentName> gates = ScoreCompositionContracts.COMPONENTS.stream()
                .filter(component -> availability.get(component) == ScoreComponentAvailability.HARD_GATE)
                .toList();
        List<ScoreComponentBreakdown> rawBreakdown = new ArrayList<>();
        for (ScoreComponentName component : ScoreCompositionContracts.COMPONENTS) {
            ComponentData data = componentData(component, input);
            rawBreakdown.add(new ScoreComponentBreakdown(
                    component,
                    data.status(),
                    data.reason(),
                    data.version(),
                    policy.globalBaseWeights().get(component),
                    weights.get(component),
                    availability.get(component),
                    data.raw(),
                    null,
                    null
            ));
        }

        if (expired) {
            return terminal(input, policy, CandidateScoreStatus.NOT_APPLICABLE,
                    CandidateScoreNotApplicableReason.EXPIRED_CONTEXT, null, anchors, gates, rawBreakdown);
        }
        if (!supported) {
            return terminal(input, policy, CandidateScoreStatus.NOT_APPLICABLE,
                    CandidateScoreNotApplicableReason.UNSUPPORTED_ENTITY_TYPE, null,
                    List.of(), List.of(), rawBreakdown);
        }
        boolean contextGate = input.contextEligibilityResult().status() == ContextEligibilityStatus.HARD_EXCLUDED;
        boolean interestGate = input.interestMatchResult().status() == InterestMatchStatus.HARD_EXCLUDED;
        if (contextGate || interestGate) {
            CandidateScoreHardExclusionReason reason = contextGate && interestGate
                    ? CandidateScoreHardExclusionReason.MULTIPLE_HARD_EXCLUSIONS
                    : contextGate ? CandidateScoreHardExclusionReason.CONTEXT_HARD_EXCLUSION
                    : CandidateScoreHardExclusionReason.INTEREST_HARD_EXCLUSION;
            return terminal(input, policy, CandidateScoreStatus.HARD_EXCLUDED,
                    null, reason, anchors, gates, rawBreakdown);
        }
        if (anchors.isEmpty()) {
            return terminal(input, policy, CandidateScoreStatus.NOT_APPLICABLE,
                    CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, null,
                    anchors, gates, rawBreakdown);
        }

        List<ScoreComponentBreakdown> scoredBreakdown = new ArrayList<>();
        for (ScoreComponentBreakdown item : rawBreakdown) {
            if (item.availability() == ScoreComponentAvailability.STRUCTURALLY_EXCLUDED) {
                scoredBreakdown.add(item);
                continue;
            }
            double value = item.availability() == ScoreComponentAvailability.SCORED
                    ? requireRaw(item) : policy.neutralPrior();
            scoredBreakdown.add(new ScoreComponentBreakdown(
                    item.component(), item.resultStatus(), item.resultNotApplicableReason(),
                    item.resultPolicyVersion(), item.globalBaseWeight(), item.entityEffectiveWeight(),
                    item.availability(), item.rawScore(), value, item.entityEffectiveWeight() * value
            ));
        }
        double scoredWeight = 0.0;
        double neutralWeight = 0.0;
        double weightedTotal = 0.0;
        for (ScoreComponentBreakdown item : scoredBreakdown) {
            if (item.availability() == ScoreComponentAvailability.SCORED) {
                scoredWeight += item.entityEffectiveWeight();
            } else if (item.availability() == ScoreComponentAvailability.NEUTRAL_FILLED) {
                neutralWeight += item.entityEffectiveWeight();
            }
            if (item.weightedContribution() != null) {
                weightedTotal += item.weightedContribution();
            }
        }
        if (Math.abs(scoredWeight + neutralWeight - 1.0) > EPSILON) {
            throw new IllegalStateException("effective weights do not sum to 1");
        }
        double finalScore = clamp(weightedTotal, policy.scoreMinimum(), policy.scoreMaximum());
        boolean hasContext = anchors.contains(ScoreComponentName.CONTEXT_MATCH);
        boolean hasInterest = anchors.contains(ScoreComponentName.INTEREST_MATCH);
        ScoreCompositionMode mode = hasContext && hasInterest
                ? ScoreCompositionMode.PERSONALIZED_CONTEXTUAL
                : hasContext ? ScoreCompositionMode.CONTEXTUAL_ONLY : ScoreCompositionMode.INTEREST_ONLY;

        return new CandidateScoreResult(
                input.userId(), input.contextId(), input.entityId(), input.entityType(),
                CandidateScoreStatus.SCORED, finalScore, mode, scoredWeight, neutralWeight,
                anchors, List.of(), null, null, policy.policyVersion(),
                policy.expectedComponentPolicyVersions(), scoredBreakdown
        );
    }

    private static void validateInterest(InterestMatchResult result) {
        requireNonBlank(result.userId(), "interest.userId");
        requireNonBlank(result.entityId(), "interest.entityId");
        requireNonBlank(result.policyVersion(), "interest.policyVersion");
        if (result.status() == InterestMatchStatus.SCORED) {
            requireScore(result.score(), "interest.score");
            if (result.notApplicableReason() != null || !result.hardExclusionFeatureIds().isEmpty()) {
                throw new IllegalArgumentException("interest scored contract invalid");
            }
        } else if (result.status() == InterestMatchStatus.NOT_APPLICABLE) {
            if (result.score() != null || result.notApplicableReason() == null
                    || !result.hardExclusionFeatureIds().isEmpty()) {
                throw new IllegalArgumentException("interest not_applicable contract invalid");
            }
        } else if (result.score() != null || result.notApplicableReason() != null
                || result.hardExclusionFeatureIds().isEmpty()) {
            throw new IllegalArgumentException("interest hard_excluded contract invalid");
        }
    }

    private static void validateEligibility(ContextEligibilityResult result) {
        requireNonBlank(result.contextId(), "eligibility.contextId");
        requireNonBlank(result.entityId(), "eligibility.entityId");
        requireNonBlank(result.policyVersion(), "eligibility.policyVersion");
        if (result.status() == ContextEligibilityStatus.ELIGIBLE
                && (result.hardExclusionReason() != null || result.notApplicableReason() != null)) {
            throw new IllegalArgumentException("eligibility eligible contract invalid");
        }
        if (result.status() == ContextEligibilityStatus.HARD_EXCLUDED
                && (result.hardExclusionReason() == null || result.notApplicableReason() != null)) {
            throw new IllegalArgumentException("eligibility hard contract invalid");
        }
        if (result.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && (result.hardExclusionReason() != null || result.notApplicableReason() == null)) {
            throw new IllegalArgumentException("eligibility not_applicable contract invalid");
        }
    }

    private static void validateContextMatch(ContextMatchResult result) {
        requireNonBlank(result.contextId(), "context.contextId");
        requireNonBlank(result.entityId(), "context.entityId");
        requireNonBlank(result.policyVersion(), "context.policyVersion");
        if (result.status() == ContextMatchStatus.SCORED) {
            requireScore(result.score(), "context.score");
            if (result.notApplicableReason() != null) {
                throw new IllegalArgumentException("context scored contract invalid");
            }
        } else if (result.score() != null || result.notApplicableReason() == null) {
            throw new IllegalArgumentException("context not_applicable contract invalid");
        }
    }

    private static void validateFreshness(FreshnessResult result) {
        requireNonBlank(result.entityId(), "freshness.entityId");
        requireNonBlank(result.policyVersion(), "freshness.policyVersion");
        if (result.status() == FreshnessStatus.SCORED) {
            requireScore(result.score(), "freshness.score");
            if (result.notApplicableReason() != null) {
                throw new IllegalArgumentException("freshness scored contract invalid");
            }
        } else if (result.score() != null || result.notApplicableReason() == null) {
            throw new IllegalArgumentException("freshness not_applicable contract invalid");
        }
    }

    private static void validatePopularity(PopularityResult result) {
        requireNonBlank(result.entityId(), "popularity.entityId");
        requireNonBlank(result.policyVersion(), "popularity.policyVersion");
        if (result.status() == PopularityStatus.SCORED) {
            requireScore(result.score(), "popularity.score");
            if (result.notApplicableReason() != null) {
                throw new IllegalArgumentException("popularity scored contract invalid");
            }
        } else if (result.score() != null || result.notApplicableReason() == null) {
            throw new IllegalArgumentException("popularity not_applicable contract invalid");
        }
    }

    private static void validateContextConsistency(
            ContextEligibilityResult eligibility,
            ContextMatchResult context
    ) {
        boolean valid;
        if (eligibility.status() == ContextEligibilityStatus.HARD_EXCLUDED) {
            valid = context.status() == ContextMatchStatus.NOT_APPLICABLE
                    && context.notApplicableReason() == ContextMatchNotApplicableReason.HARD_CONTEXT_NOT_ELIGIBLE;
        } else if (eligibility.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && eligibility.notApplicableReason() == ContextEligibilityNotApplicableReason.EXPIRED_CONTEXT) {
            valid = context.status() == ContextMatchStatus.NOT_APPLICABLE
                    && context.notApplicableReason() == ContextMatchNotApplicableReason.EXPIRED_CONTEXT;
        } else if (eligibility.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && eligibility.notApplicableReason() == ContextEligibilityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
            valid = context.status() == ContextMatchStatus.NOT_APPLICABLE
                    && context.notApplicableReason() == ContextMatchNotApplicableReason.UNSUPPORTED_ENTITY_TYPE;
        } else if (eligibility.status() == ContextEligibilityStatus.ELIGIBLE
                || (eligibility.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && eligibility.notApplicableReason() == ContextEligibilityNotApplicableReason.NO_HARD_CONTEXT_CLAUSES)) {
            valid = context.status() == ContextMatchStatus.SCORED
                    || (context.status() == ContextMatchStatus.NOT_APPLICABLE
                    && (context.notApplicableReason() == ContextMatchNotApplicableReason.NO_SOFT_CONTEXT_CLAUSES
                    || context.notApplicableReason() == ContextMatchNotApplicableReason.NO_OBSERVABLE_CONTEXT_GROUPS));
        } else {
            valid = false;
        }
        if (!valid) {
            throw new IllegalArgumentException("Context Eligibility and Context Match are inconsistent");
        }
    }

    private static void validateEntityComponentContract(
            RecommendationEntityType entityType,
            FreshnessResult freshness,
            PopularityResult popularity
    ) {
        boolean temporal = entityType == RecommendationEntityType.POST || entityType == RecommendationEntityType.JOURNEY;
        if (temporal) {
            if (freshness.status() == FreshnessStatus.NOT_APPLICABLE
                    && freshness.notApplicableReason() == FreshnessNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
                throw new IllegalArgumentException("Freshness unsupported for temporal entity");
            }
            if (popularity.status() == PopularityStatus.NOT_APPLICABLE
                    && popularity.notApplicableReason() == PopularityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
                throw new IllegalArgumentException("Popularity unsupported for temporal entity");
            }
        } else if (entityType == RecommendationEntityType.PLACE
                || entityType == RecommendationEntityType.CREW
                || entityType == RecommendationEntityType.USER) {
            if (freshness.status() != FreshnessStatus.NOT_APPLICABLE
                    || freshness.notApplicableReason() != FreshnessNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
                throw new IllegalArgumentException("Freshness must be structurally unsupported");
            }
            if (popularity.status() != PopularityStatus.NOT_APPLICABLE
                    || popularity.notApplicableReason() != PopularityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
                throw new IllegalArgumentException("Popularity must be structurally unsupported");
            }
        }
    }

    private static Map<ScoreComponentName, Double> effectiveWeights(
            ScoreCompositionPolicy policy,
            RecommendationEntityType entityType
    ) {
        Map<ScoreComponentName, Double> result = new EnumMap<>(ScoreComponentName.class);
        for (ScoreComponentName component : ScoreCompositionContracts.COMPONENTS) {
            result.put(component, 0.0);
        }
        List<ScoreComponentName> profile = policy.entityComponentEligibility().get(entityType);
        if (profile == null) {
            return result;
        }
        double sum = 0.0;
        for (ScoreComponentName component : profile) {
            sum += policy.globalBaseWeights().get(component);
        }
        if (sum == 0.0) {
            return result;
        }
        for (ScoreComponentName component : profile) {
            result.put(component, policy.globalBaseWeights().get(component) / sum);
        }
        return result;
    }

    private static ScoreComponentAvailability baseAvailability(
            ScoreComponentName component,
            ScoreCandidateInput input,
            boolean supported,
            boolean expired
    ) {
        if (!supported) {
            return expired && component == ScoreComponentName.CONTEXT_MATCH
                    ? ScoreComponentAvailability.HARD_GATE
                    : ScoreComponentAvailability.STRUCTURALLY_EXCLUDED;
        }
        if (component == ScoreComponentName.CONTEXT_MATCH) {
            if (expired || input.contextEligibilityResult().status() == ContextEligibilityStatus.HARD_EXCLUDED) {
                return ScoreComponentAvailability.HARD_GATE;
            }
            return input.contextMatchResult().status() == ContextMatchStatus.SCORED
                    ? ScoreComponentAvailability.SCORED : ScoreComponentAvailability.NEUTRAL_FILLED;
        }
        if (component == ScoreComponentName.INTEREST_MATCH) {
            if (input.interestMatchResult().status() == InterestMatchStatus.HARD_EXCLUDED) {
                return ScoreComponentAvailability.HARD_GATE;
            }
            return input.interestMatchResult().status() == InterestMatchStatus.SCORED
                    ? ScoreComponentAvailability.SCORED : ScoreComponentAvailability.NEUTRAL_FILLED;
        }
        if (component == ScoreComponentName.FRESHNESS) {
            if (input.freshnessResult().status() == FreshnessStatus.SCORED) {
                return ScoreComponentAvailability.SCORED;
            }
            return isTemporal(input.entityType())
                    ? ScoreComponentAvailability.NEUTRAL_FILLED
                    : ScoreComponentAvailability.STRUCTURALLY_EXCLUDED;
        }
        if (input.popularityResult().status() == PopularityStatus.SCORED) {
            return ScoreComponentAvailability.SCORED;
        }
        return isTemporal(input.entityType())
                ? ScoreComponentAvailability.NEUTRAL_FILLED
                : ScoreComponentAvailability.STRUCTURALLY_EXCLUDED;
    }

    private static ComponentData componentData(ScoreComponentName component, ScoreCandidateInput input) {
        if (component == ScoreComponentName.CONTEXT_MATCH) {
            return new ComponentData(
                    input.contextMatchResult().status().wireValue(),
                    input.contextMatchResult().notApplicableReason() == null ? null
                            : input.contextMatchResult().notApplicableReason().wireValue(),
                    input.contextMatchResult().policyVersion(), input.contextMatchResult().score());
        }
        if (component == ScoreComponentName.INTEREST_MATCH) {
            return new ComponentData(
                    input.interestMatchResult().status().wireValue(),
                    input.interestMatchResult().notApplicableReason() == null ? null
                            : input.interestMatchResult().notApplicableReason().wireValue(),
                    input.interestMatchResult().policyVersion(), input.interestMatchResult().score());
        }
        if (component == ScoreComponentName.FRESHNESS) {
            return new ComponentData(
                    input.freshnessResult().status().wireValue(),
                    input.freshnessResult().notApplicableReason() == null ? null
                            : input.freshnessResult().notApplicableReason().wireValue(),
                    input.freshnessResult().policyVersion(), input.freshnessResult().score());
        }
        return new ComponentData(
                input.popularityResult().status().wireValue(),
                input.popularityResult().notApplicableReason() == null ? null
                        : input.popularityResult().notApplicableReason().wireValue(),
                input.popularityResult().policyVersion(), input.popularityResult().score());
    }

    private static CandidateScoreResult terminal(
            ScoreCandidateInput input,
            ScoreCompositionPolicy policy,
            CandidateScoreStatus status,
            CandidateScoreNotApplicableReason notApplicableReason,
            CandidateScoreHardExclusionReason hardExclusionReason,
            List<ScoreComponentName> anchors,
            List<ScoreComponentName> gates,
            List<ScoreComponentBreakdown> breakdown
    ) {
        return new CandidateScoreResult(
                input.userId(), input.contextId(), input.entityId(), input.entityType(), status,
                null, null, null, null, anchors, gates, notApplicableReason, hardExclusionReason,
                policy.policyVersion(), policy.expectedComponentPolicyVersions(), breakdown
        );
    }

    private static boolean isTemporal(RecommendationEntityType type) {
        return type == RecommendationEntityType.POST || type == RecommendationEntityType.JOURNEY;
    }

    private static double requireRaw(ScoreComponentBreakdown item) {
        if (item.rawScore() == null) {
            throw new IllegalStateException("Scored component has null raw score: " + item.component().wireValue());
        }
        return item.rawScore();
    }

    private static void checkEntityId(String label, String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(label + " entityId mismatch");
        }
    }

    private static void checkEntityType(
            String label,
            RecommendationEntityType actual,
            RecommendationEntityType expected
    ) {
        if (actual != expected) {
            throw new IllegalArgumentException(label + " entityType mismatch");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be nonblank");
        }
    }

    private static void requireScore(Double value, String label) {
        if (value == null || !Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " must be finite 0..1");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
