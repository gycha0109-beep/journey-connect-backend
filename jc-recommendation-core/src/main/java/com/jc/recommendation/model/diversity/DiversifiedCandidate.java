package com.jc.recommendation.model.diversity;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.ranking.RankingSortKey;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import java.util.List;
import java.util.Objects;

public record DiversifiedCandidate(
        String entityId, RecommendationEntityType entityType, double score, double scoredWeight,
        double neutralFilledWeight, ScoreCompositionMode compositionMode, String scorePolicyVersion,
        int baseAbsoluteRank, int diversifiedAbsoluteRank, int displacement, int promotionDistance,
        int demotionDistance, RankingSortKey baseSortKey, DiversityCandidateMetadata diversityMetadata,
        DiversitySelectionReason selectionReason, List<DiversityDimension> appliedRelaxations,
        List<DiversityDimension> violatedDimensionsAtSelection
) {
    public DiversifiedCandidate {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(compositionMode, "compositionMode");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(baseSortKey, "baseSortKey");
        Objects.requireNonNull(diversityMetadata, "diversityMetadata");
        Objects.requireNonNull(selectionReason, "selectionReason");
        appliedRelaxations = List.copyOf(appliedRelaxations);
        violatedDimensionsAtSelection = List.copyOf(violatedDimensionsAtSelection);
    }
}
