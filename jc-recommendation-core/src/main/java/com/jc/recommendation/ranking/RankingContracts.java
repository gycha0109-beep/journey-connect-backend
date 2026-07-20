package com.jc.recommendation.ranking;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.policy.RankingPolicy;
import com.jc.recommendation.policy.RankingPolicies;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RankingContracts {
    static final double EPSILON = 1e-12;
    static final List<RecommendationEntityType> RANKING_ENTITY_TYPES = List.of(
            RecommendationEntityType.POST,
            RecommendationEntityType.JOURNEY,
            RecommendationEntityType.PLACE,
            RecommendationEntityType.CREW
    );

    private RankingContracts() {
    }

    static void validatePolicy(RankingPolicy policy) {
        nonBlank(policy.policyVersion(), "policyVersion");
        nonBlank(policy.expectedScorePolicyVersion(), "expectedScorePolicyVersion");
        positive(policy.maxInputCandidates(), "maxInputCandidates");
        positive(policy.defaultResultLimit(), "defaultResultLimit");
        positive(policy.hardResultLimit(), "hardResultLimit");
        if (policy.defaultResultLimit() > policy.hardResultLimit()) {
            throw new IllegalArgumentException("defaultResultLimit must not exceed hardResultLimit");
        }
        if (policy.hardResultLimit() > policy.maxInputCandidates()) {
            throw new IllegalArgumentException("hardResultLimit must not exceed maxInputCandidates");
        }
        exactEntitySet(policy.eligibleEntityTypes(), true, "eligibleEntityTypes");
        exactEntitySet(policy.entityTypeOrder(), false, "entityTypeOrder");
        equal("scored", policy.rankedStatus(), "rankedStatus must be scored");
        equal("descending", policy.scoreDirection(), "scoreDirection must be descending");
        equal("exact_number_equality", policy.scoreEquality(), "scoreEquality must be exact_number_equality");
        equal("ascending", policy.neutralFilledWeightDirection(), "neutralFilledWeightDirection must be ascending");
        equal("utf16_code_unit_ascending", policy.entityIdComparison(), "entityIdComparison must be utf16_code_unit_ascending");
        equal("ranking-cursor-v1", policy.cursorVersion(), "cursorVersion must be ranking-cursor-v1");
        equal("disabled_after_base_ranking", policy.diversityStage(), "diversity stage cannot be enabled");
        equal("disabled_after_diversity", policy.explorationStage(), "exploration stage cannot be enabled");
        equal("after_future_reranking_stages", policy.paginationStage(), "paginationStage is invalid");
        equal("after_cursor_boundary", policy.resultLimitStage(), "resultLimitStage is invalid");
        if (policy.policyVersion().equals(RankingPolicies.V1.policyVersion())
                && (!policy.expectedScorePolicyVersion().equals("score-composition-v1")
                || policy.maxInputCandidates() != 100
                || policy.defaultResultLimit() != 20
                || policy.hardResultLimit() != 30
                || !policy.entityTypeOrder().equals(RANKING_ENTITY_TYPES))) {
            throw new IllegalArgumentException(
                    "ranking-v1 content is immutable; changed policy content requires a new policyVersion"
            );
        }
    }

    static void validateCandidate(CandidateScoreResult candidate, int index) {
        String label = "candidates[" + index + "]";
        nonBlank(candidate.userId(), label + ".userId");
        nonBlank(candidate.contextId(), label + ".contextId");
        nonBlank(candidate.entityId(), label + ".entityId");
        if (candidate.entityType() == RecommendationEntityType.USER) {
            throw new IllegalArgumentException(label + ".entityType user is not accepted by Ranking");
        }
        nonBlank(candidate.policyVersion(), label + ".policyVersion");
        if (candidate.status() == CandidateScoreStatus.SCORED) {
            score(candidate.score(), label + ".score");
            score(candidate.scoredWeight(), label + ".scoredWeight");
            score(candidate.neutralFilledWeight(), label + ".neutralFilledWeight");
            if (candidate.scoredWeight() <= 0.0
                    || Math.abs(candidate.scoredWeight() + candidate.neutralFilledWeight() - 1.0) > EPSILON) {
                throw new IllegalArgumentException(label + " scored weight contract is invalid");
            }
            if (candidate.compositionMode() == null
                    || candidate.notApplicableReason() != null
                    || candidate.hardExclusionReason() != null) {
                throw new IllegalArgumentException(label + " scored reason contract is invalid");
            }
        } else if (candidate.status() == CandidateScoreStatus.NOT_APPLICABLE) {
            if (candidate.score() != null || candidate.scoredWeight() != null
                    || candidate.neutralFilledWeight() != null || candidate.compositionMode() != null) {
                throw new IllegalArgumentException(label + " not_applicable numeric contract is invalid");
            }
            if (candidate.notApplicableReason() == null || candidate.hardExclusionReason() != null) {
                throw new IllegalArgumentException(label + " not_applicable reason contract is invalid");
            }
        } else {
            if (candidate.score() != null || candidate.scoredWeight() != null
                    || candidate.neutralFilledWeight() != null || candidate.compositionMode() != null) {
                throw new IllegalArgumentException(label + " hard_excluded numeric contract is invalid");
            }
            if (candidate.notApplicableReason() != null || candidate.hardExclusionReason() == null) {
                throw new IllegalArgumentException(label + " hard_excluded reason contract is invalid");
            }
        }
    }

    static int entityRank(RankingPolicy policy, RecommendationEntityType entityType) {
        int rank = policy.entityTypeOrder().indexOf(entityType);
        if (rank < 0) {
            throw new IllegalArgumentException("entity type is not ranked by policy: " + entityType.wireValue());
        }
        return rank;
    }

    static void nonBlank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be nonblank");
        }
    }

    static void positive(int value, String label) {
        if (value < 1) {
            throw new IllegalArgumentException(label + " must be a positive safe integer");
        }
    }

    private static void score(Double value, String label) {
        if (value == null || !Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " must be finite 0..1");
        }
    }

    private static void exactEntitySet(
            List<RecommendationEntityType> values,
            boolean orderRequired,
            String label
    ) {
        Set<RecommendationEntityType> unique = new HashSet<>(values);
        if (values.size() != RANKING_ENTITY_TYPES.size()
                || unique.size() != RANKING_ENTITY_TYPES.size()
                || !unique.containsAll(RANKING_ENTITY_TYPES)) {
            throw new IllegalArgumentException(label + " must contain every ranking entity exactly once");
        }
        if (orderRequired && !values.equals(RANKING_ENTITY_TYPES)) {
            throw new IllegalArgumentException(label + " has invalid order");
        }
    }

    private static void equal(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(message);
        }
    }
}
