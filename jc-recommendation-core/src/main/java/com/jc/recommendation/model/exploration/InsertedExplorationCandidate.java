package com.jc.recommendation.model.exploration;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.model.score.CandidateScoreStatus;

import java.util.List;
import java.util.Objects;

public record InsertedExplorationCandidate(
        ExplorationCandidateOrigin origin,
        int absoluteRank,
        Integer diversifiedAbsoluteRank,
        Integer baseAbsoluteRank,
        String entityId,
        RecommendationEntityType entityType,
        Double score,
        Double scoredWeight,
        Double neutralFilledWeight,
        ScoreCompositionMode compositionMode,
        String scorePolicyVersion,
        CandidateScoreStatus sourceStatus,
        CandidateScoreNotApplicableReason sourceNotApplicableReason,
        double explorationQualityScore,
        List<ExplorationQualityEvidence> qualityEvidence,
        int availableWeightTotal,
        Double freshnessRawScore,
        Double popularityRawScore,
        int recentExposureCount,
        long seededTieBreakKey,
        int explorationPoolRank,
        int targetInsertionRank,
        String explorationPolicyVersion,
        DiversityCandidateMetadata diversityMetadata
) implements ExplorationFinalCandidate {
    public InsertedExplorationCandidate {
        if (origin != ExplorationCandidateOrigin.EXPLORATION) {
            throw new IllegalArgumentException("origin must be exploration");
        }
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(sourceStatus, "sourceStatus");
        Objects.requireNonNull(sourceNotApplicableReason, "sourceNotApplicableReason");
        qualityEvidence = List.copyOf(qualityEvidence);
        Objects.requireNonNull(explorationPolicyVersion, "explorationPolicyVersion");
        Objects.requireNonNull(diversityMetadata, "diversityMetadata");
    }
}
