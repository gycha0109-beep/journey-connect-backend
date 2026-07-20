package com.jc.recommendation.integration;

import com.jc.recommendation.policy.CandidateLimitPolicies;
import com.jc.recommendation.policy.DiversityEnabledRankingPolicy;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.ExplorationEnabledRankingPolicy;
import com.jc.recommendation.policy.ExplorationPolicies;
import com.jc.recommendation.policy.RankingIntegrationPolicies;
import com.jc.recommendation.policy.RankingPolicies;

import java.nio.charset.StandardCharsets;

final class RankingIntegrationContracts {
    private RankingIntegrationContracts() {
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

    static void seed(String value, String label) {
        nonBlank(value, label);
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 1 || bytes > 128) {
            throw new IllegalArgumentException(label + " must be 1..128 UTF-8 bytes");
        }
    }

    static void validateV2(DiversityEnabledRankingPolicy policy) {
        nonBlank(policy.policyVersion(), "policyVersion");
        if (!policy.baseRankingPolicyVersion().equals(RankingPolicies.V1.policyVersion())) throw new IllegalArgumentException("baseRankingPolicyVersion must be ranking-v1");
        if (!policy.expectedScorePolicyVersion().equals(RankingPolicies.V1.expectedScorePolicyVersion())) throw new IllegalArgumentException("expectedScorePolicyVersion must match ranking-v1");
        nonBlank(policy.expectedDiversityPolicyVersion(), "expectedDiversityPolicyVersion");
        if (policy.maxInputCandidates() != CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE) throw new IllegalArgumentException("maxInputCandidates must match candidate limit policy");
        positive(policy.defaultResultLimit(), "defaultResultLimit");
        positive(policy.hardResultLimit(), "hardResultLimit");
        if (policy.defaultResultLimit() > policy.hardResultLimit()) throw new IllegalArgumentException("defaultResultLimit must not exceed hardResultLimit");
        if (policy.hardResultLimit() > policy.maxInputCandidates()) throw new IllegalArgumentException("hardResultLimit must not exceed maxInputCandidates");
        if (!policy.eligibleEntityTypes().equals(RankingPolicies.V1.eligibleEntityTypes())) throw new IllegalArgumentException("eligibleEntityTypes must exactly match ranking-v1");
        if (!policy.entityTypeOrder().equals(RankingPolicies.V1.entityTypeOrder())) throw new IllegalArgumentException("entityTypeOrder must exactly match ranking-v1");
        if (!policy.rankedStatus().equals(RankingPolicies.V1.rankedStatus())
                || !policy.scoreDirection().equals(RankingPolicies.V1.scoreDirection())
                || !policy.scoreEquality().equals(RankingPolicies.V1.scoreEquality())
                || !policy.neutralFilledWeightDirection().equals(RankingPolicies.V1.neutralFilledWeightDirection())
                || !policy.entityIdComparison().equals(RankingPolicies.V1.entityIdComparison())) {
            throw new IllegalArgumentException("ranking-v2 base semantics mismatch");
        }
        if (!policy.cursorVersion().equals("ranking-cursor-v2")
                || !policy.diversityStage().equals("enabled_after_base_ranking")
                || !policy.explorationStage().equals("disabled_after_diversity")
                || !policy.paginationStage().equals("after_diversity_and_exploration")
                || !policy.resultLimitStage().equals("after_cursor_boundary")
                || !policy.metadataCoverage().equals("exactly_scored_candidates")
                || !policy.finalRankSource().equals("diversified_absolute_rank")) {
            throw new IllegalArgumentException("ranking-v2 integration semantics mismatch");
        }
        if (policy.policyVersion().equals("ranking-v2") && (
                policy.defaultResultLimit() != CandidateLimitPolicies.DEFAULT_RESULT_LIMIT
                        || policy.hardResultLimit() != CandidateLimitPolicies.HARD_RESULT_LIMIT
                        || !policy.expectedDiversityPolicyVersion().equals(DiversityPolicies.V1.policyVersion()))) {
            throw new IllegalArgumentException("ranking-v2 content is immutable; changed configurable content requires a new policyVersion");
        }
    }

    static void validateV3(ExplorationEnabledRankingPolicy policy) {
        nonBlank(policy.policyVersion(), "policyVersion");
        if (!policy.baseRankingPolicyVersion().equals(RankingPolicies.V1.policyVersion())) throw new IllegalArgumentException("baseRankingPolicyVersion must be ranking-v1");
        if (!policy.baseIntegrationPolicyVersion().equals(RankingIntegrationPolicies.V2.policyVersion())) throw new IllegalArgumentException("baseIntegrationPolicyVersion must be ranking-v2");
        if (!policy.expectedScorePolicyVersion().equals(RankingIntegrationPolicies.V2.expectedScorePolicyVersion())) throw new IllegalArgumentException("expectedScorePolicyVersion must match ranking-v2");
        nonBlank(policy.expectedDiversityPolicyVersion(), "expectedDiversityPolicyVersion");
        nonBlank(policy.expectedExplorationPolicyVersion(), "expectedExplorationPolicyVersion");
        if (policy.maxInputCandidates() != CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE) throw new IllegalArgumentException("maxInputCandidates must match candidate limit policy");
        positive(policy.defaultResultLimit(), "defaultResultLimit");
        positive(policy.hardResultLimit(), "hardResultLimit");
        if (policy.defaultResultLimit() > policy.hardResultLimit()) throw new IllegalArgumentException("defaultResultLimit must not exceed hardResultLimit");
        if (policy.hardResultLimit() > policy.maxInputCandidates()) throw new IllegalArgumentException("hardResultLimit must not exceed maxInputCandidates");
        if (!policy.eligibleEntityTypes().equals(RankingIntegrationPolicies.V2.eligibleEntityTypes())) throw new IllegalArgumentException("eligibleEntityTypes must exactly match ranking-v2");
        if (!policy.entityTypeOrder().equals(RankingIntegrationPolicies.V2.entityTypeOrder())) throw new IllegalArgumentException("entityTypeOrder must exactly match ranking-v2");
        if (!policy.cursorVersion().equals("ranking-cursor-v3")
                || !policy.diversityStage().equals("enabled_after_base_ranking")
                || !policy.explorationStage().equals("enabled_after_diversity")
                || !policy.paginationStage().equals("after_exploration")
                || !policy.resultLimitStage().equals("after_cursor_boundary")
                || !policy.scoredMetadataCoverage().equals("exactly_scored_candidates")
                || !policy.explorationMetadataCoverage().equals("exactly_structurally_eligible_candidates")
                || !policy.terminalMigration().equals("inserted_exploration_removed_from_terminal")
                || !policy.finalRankSource().equals("exploration_final_absolute_rank")) {
            throw new IllegalArgumentException("ranking-v3 integration semantics mismatch");
        }
        if (policy.policyVersion().equals("ranking-v3") && (
                policy.defaultResultLimit() != CandidateLimitPolicies.DEFAULT_RESULT_LIMIT
                        || policy.hardResultLimit() != CandidateLimitPolicies.HARD_RESULT_LIMIT
                        || !policy.expectedDiversityPolicyVersion().equals(DiversityPolicies.V1.policyVersion())
                        || !policy.expectedExplorationPolicyVersion().equals(ExplorationPolicies.V1.policyVersion()))) {
            throw new IllegalArgumentException("ranking-v3 content is immutable; changed configurable content requires a new policyVersion");
        }
    }
}
