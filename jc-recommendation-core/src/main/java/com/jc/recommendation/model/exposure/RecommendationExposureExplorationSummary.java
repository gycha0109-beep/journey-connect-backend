package com.jc.recommendation.model.exposure;

import com.jc.recommendation.model.exploration.ExplorationResultStatus;
import com.jc.recommendation.model.exploration.ExplorationSeedAlgorithm;
import java.util.List;
import java.util.Objects;

public record RecommendationExposureExplorationSummary(
        ExplorationResultStatus status, int structurallyEligibleCandidateCount, int eligibleCandidateCount,
        int insertedCandidateCount, int skippedSlotCount, int statusReasonRejectedCount,
        int entityTypeRejectedCount, int exposureRejectedCount, int qualityEvidenceRejectedCount,
        int qualityFloorRejectedCount, int diversityGuardRejectedEvaluationCount, List<Integer> insertedTargetRanks,
        String policyVersion, ExplorationSeedAlgorithm seedAlgorithm,
        List<RecommendationExposureExplorationSlotDecision> slotDecisions
) {
    public RecommendationExposureExplorationSummary {
        Objects.requireNonNull(status, "status");
        insertedTargetRanks = List.copyOf(insertedTargetRanks);
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(seedAlgorithm, "seedAlgorithm");
        slotDecisions = List.copyOf(slotDecisions);
    }
}
