package com.jc.recommendation.model.exploration;

import java.util.List;
import java.util.Objects;

public record ExplorationSummary(
        ExplorationResultStatus status,
        int structurallyEligibleCandidateCount,
        int eligibleCandidateCount,
        int insertedCandidateCount,
        int skippedSlotCount,
        int statusReasonRejectedCount,
        int entityTypeRejectedCount,
        int exposureRejectedCount,
        int qualityEvidenceRejectedCount,
        int qualityFloorRejectedCount,
        int diversityGuardRejectedEvaluationCount,
        List<Integer> insertedTargetRanks,
        String policyVersion,
        ExplorationSeedAlgorithm seedAlgorithm,
        List<ExplorationSlotDecision> slotDecisions
) {
    public ExplorationSummary {
        Objects.requireNonNull(status, "status");
        insertedTargetRanks = List.copyOf(insertedTargetRanks);
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(seedAlgorithm, "seedAlgorithm");
        slotDecisions = List.copyOf(slotDecisions);
    }
}
