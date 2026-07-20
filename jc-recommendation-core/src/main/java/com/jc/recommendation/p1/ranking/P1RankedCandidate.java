package com.jc.recommendation.p1.ranking;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import java.util.List;
import java.util.Objects;

public record P1RankedCandidate(
        int absoluteRank,
        int baseRank,
        String entityId,
        RecommendationEntityType entityType,
        double score,
        double baseScore,
        double contextScore,
        double interestScore,
        double freshnessScore,
        double rawPopularityScore,
        double adjustedPopularityScore,
        double lowExposureBoost,
        int recentExposureCount,
        List<DiversityDimension> appliedRelaxations,
        DiversityCandidateMetadata diversityMetadata) {

    public P1RankedCandidate {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        appliedRelaxations = List.copyOf(Objects.requireNonNull(appliedRelaxations, "appliedRelaxations"));
        Objects.requireNonNull(diversityMetadata, "diversityMetadata");
        if (absoluteRank < 1 || baseRank < 1 || recentExposureCount < 0) {
            throw new IllegalArgumentException("rank and exposure values are invalid");
        }
        validateUnit(score, "score");
        validateUnit(baseScore, "baseScore");
        validateUnit(contextScore, "contextScore");
        validateUnit(interestScore, "interestScore");
        validateUnit(freshnessScore, "freshnessScore");
        validateUnit(rawPopularityScore, "rawPopularityScore");
        validateUnit(adjustedPopularityScore, "adjustedPopularityScore");
        validateUnit(lowExposureBoost, "lowExposureBoost");
    }

    private static void validateUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1]");
        }
    }
}
