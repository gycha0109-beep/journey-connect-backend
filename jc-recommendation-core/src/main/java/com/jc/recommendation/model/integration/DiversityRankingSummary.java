package com.jc.recommendation.model.integration;

import com.jc.recommendation.model.diversity.DiversityDimensionCounts;
import com.jc.recommendation.model.diversity.DiversityRerankStatus;

import java.util.Objects;

public record DiversityRankingSummary(
        DiversityRerankStatus status,
        int movedCandidateCount,
        int maxPromotionObserved,
        int maxDemotionObserved,
        int movementBoundForcedCount,
        DiversityDimensionCounts relaxationCountByDimension,
        DiversityDimensionCounts violationCountByDimension,
        DiversityDimensionCounts missingMetadataCountByDimension
) {
    public DiversityRankingSummary {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(relaxationCountByDimension, "relaxationCountByDimension");
        Objects.requireNonNull(violationCountByDimension, "violationCountByDimension");
        Objects.requireNonNull(missingMetadataCountByDimension, "missingMetadataCountByDimension");
    }
}
