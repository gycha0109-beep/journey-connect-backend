package com.jc.recommendation.policy;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationQualityComponent;
import com.jc.recommendation.model.exploration.ExplorationQualityWeights;
import com.jc.recommendation.model.exploration.ExplorationSeedAlgorithm;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ExplorationPolicy(
        String policyVersion,
        Instant effectiveFrom,
        String expectedRankingPolicyVersion,
        String expectedScorePolicyVersion,
        String expectedDiversityPolicyVersion,
        int maximumCandidateCount,
        List<RecommendationEntityType> eligibleEntityTypes,
        CandidateScoreStatus eligibleStatus,
        CandidateScoreNotApplicableReason eligibleNotApplicableReason,
        List<ExplorationQualityComponent> qualityComponents,
        ExplorationQualityWeights qualityWeights,
        int minimumAvailableQualityComponents,
        double minimumQualityScore,
        int exposureCountWindowDays,
        int maximumRecentExposureCount,
        int maximumInsertions,
        List<Integer> insertionRanks,
        String selectionOrder,
        ExplorationSeedAlgorithm seedAlgorithm,
        int maximumSeedUtf8Bytes,
        String diversityGuard,
        String diversityRelaxation,
        String personalizedCandidateRemoval,
        String explorationScoreImpersonation,
        String hardExcludedResurrection,
        String paginationStage
) implements VersionedPolicy {
    public ExplorationPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(expectedRankingPolicyVersion, "expectedRankingPolicyVersion");
        Objects.requireNonNull(expectedScorePolicyVersion, "expectedScorePolicyVersion");
        Objects.requireNonNull(expectedDiversityPolicyVersion, "expectedDiversityPolicyVersion");
        eligibleEntityTypes = List.copyOf(eligibleEntityTypes);
        Objects.requireNonNull(eligibleStatus, "eligibleStatus");
        Objects.requireNonNull(eligibleNotApplicableReason, "eligibleNotApplicableReason");
        qualityComponents = List.copyOf(qualityComponents);
        Objects.requireNonNull(qualityWeights, "qualityWeights");
        insertionRanks = List.copyOf(insertionRanks);
        Objects.requireNonNull(selectionOrder, "selectionOrder");
        Objects.requireNonNull(seedAlgorithm, "seedAlgorithm");
        Objects.requireNonNull(diversityGuard, "diversityGuard");
        Objects.requireNonNull(diversityRelaxation, "diversityRelaxation");
        Objects.requireNonNull(personalizedCandidateRemoval, "personalizedCandidateRemoval");
        Objects.requireNonNull(explorationScoreImpersonation, "explorationScoreImpersonation");
        Objects.requireNonNull(hardExcludedResurrection, "hardExcludedResurrection");
        Objects.requireNonNull(paginationStage, "paginationStage");
    }
}
