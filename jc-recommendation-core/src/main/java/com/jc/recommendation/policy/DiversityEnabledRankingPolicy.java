package com.jc.recommendation.policy;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DiversityEnabledRankingPolicy(
        String policyVersion,
        Instant effectiveFrom,
        String baseRankingPolicyVersion,
        String expectedScorePolicyVersion,
        String expectedDiversityPolicyVersion,
        int maxInputCandidates,
        int defaultResultLimit,
        int hardResultLimit,
        List<RecommendationEntityType> eligibleEntityTypes,
        List<RecommendationEntityType> entityTypeOrder,
        String rankedStatus,
        String scoreDirection,
        String scoreEquality,
        String neutralFilledWeightDirection,
        String entityIdComparison,
        String cursorVersion,
        String diversityStage,
        String explorationStage,
        String paginationStage,
        String resultLimitStage,
        String metadataCoverage,
        String finalRankSource
) implements VersionedPolicy {
    public DiversityEnabledRankingPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(baseRankingPolicyVersion, "baseRankingPolicyVersion");
        Objects.requireNonNull(expectedScorePolicyVersion, "expectedScorePolicyVersion");
        Objects.requireNonNull(expectedDiversityPolicyVersion, "expectedDiversityPolicyVersion");
        eligibleEntityTypes = List.copyOf(eligibleEntityTypes);
        entityTypeOrder = List.copyOf(entityTypeOrder);
        Objects.requireNonNull(rankedStatus, "rankedStatus");
        Objects.requireNonNull(scoreDirection, "scoreDirection");
        Objects.requireNonNull(scoreEquality, "scoreEquality");
        Objects.requireNonNull(neutralFilledWeightDirection, "neutralFilledWeightDirection");
        Objects.requireNonNull(entityIdComparison, "entityIdComparison");
        Objects.requireNonNull(cursorVersion, "cursorVersion");
        Objects.requireNonNull(diversityStage, "diversityStage");
        Objects.requireNonNull(explorationStage, "explorationStage");
        Objects.requireNonNull(paginationStage, "paginationStage");
        Objects.requireNonNull(resultLimitStage, "resultLimitStage");
        Objects.requireNonNull(metadataCoverage, "metadataCoverage");
        Objects.requireNonNull(finalRankSource, "finalRankSource");
    }
}
