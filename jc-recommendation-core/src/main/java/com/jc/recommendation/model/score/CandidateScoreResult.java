package com.jc.recommendation.model.score;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.List;
import java.util.Objects;

public record CandidateScoreResult(
        String userId,
        String contextId,
        String entityId,
        RecommendationEntityType entityType,
        CandidateScoreStatus status,
        Double score,
        ScoreCompositionMode compositionMode,
        Double scoredWeight,
        Double neutralFilledWeight,
        List<ScoreComponentName> anchorComponents,
        List<ScoreComponentName> hardGateComponents,
        CandidateScoreNotApplicableReason notApplicableReason,
        CandidateScoreHardExclusionReason hardExclusionReason,
        String policyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        List<ScoreComponentBreakdown> breakdown
) {
    public CandidateScoreResult {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(status, "status");
        anchorComponents = List.copyOf(anchorComponents);
        hardGateComponents = List.copyOf(hardGateComponents);
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        breakdown = List.copyOf(breakdown);
    }
}
