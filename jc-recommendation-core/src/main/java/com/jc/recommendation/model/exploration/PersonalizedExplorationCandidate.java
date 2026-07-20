package com.jc.recommendation.model.exploration;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.diversity.DiversitySelectionReason;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.ranking.RankingSortKey;
import com.jc.recommendation.model.score.ScoreCompositionMode;

import java.util.List;
import java.util.Objects;

public record PersonalizedExplorationCandidate(
        ExplorationCandidateOrigin origin,
        int absoluteRank,
        int diversifiedAbsoluteRank,
        int baseAbsoluteRank,
        String entityId,
        RecommendationEntityType entityType,
        double score,
        double scoredWeight,
        double neutralFilledWeight,
        ScoreCompositionMode compositionMode,
        String scorePolicyVersion,
        RankingSortKey baseSortKey,
        DiversityCandidateMetadata diversityMetadata,
        DiversitySelectionReason selectionReason,
        List<DiversityDimension> appliedRelaxations,
        List<DiversityDimension> violatedDimensionsAtSelection,
        int displacement,
        int promotionDistance,
        int demotionDistance
) implements ExplorationFinalCandidate {
    public PersonalizedExplorationCandidate {
        if (origin != ExplorationCandidateOrigin.PERSONALIZED) {
            throw new IllegalArgumentException("origin must be personalized");
        }
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
