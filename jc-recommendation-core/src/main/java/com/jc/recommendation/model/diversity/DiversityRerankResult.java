package com.jc.recommendation.model.diversity;

import java.util.List;
import java.util.Objects;

public record DiversityRerankResult(
        String rankingSnapshotId, String metadataSnapshotId, String rankingPolicyVersion,
        String scorePolicyVersion, String diversityPolicyVersion, DiversityRerankStatus status,
        int inputCount, int outputCount, int movedCandidateCount, int maxPromotionObserved,
        int maxDemotionObserved, int movementBoundForcedCount,
        DiversityDimensionCounts relaxationCountByDimension,
        DiversityDimensionCounts violationCountByDimension,
        DiversityDimensionCounts missingMetadataCountByDimension,
        List<DiversifiedCandidate> diversifiedCandidates
) {
    public DiversityRerankResult {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(relaxationCountByDimension, "relaxationCountByDimension");
        Objects.requireNonNull(violationCountByDimension, "violationCountByDimension");
        Objects.requireNonNull(missingMetadataCountByDimension, "missingMetadataCountByDimension");
        diversifiedCandidates = List.copyOf(diversifiedCandidates);
    }
}
