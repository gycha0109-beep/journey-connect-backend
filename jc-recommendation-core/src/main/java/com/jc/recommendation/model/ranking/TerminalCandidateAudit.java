package com.jc.recommendation.model.ranking;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.CandidateScoreHardExclusionReason;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreStatus;

import java.util.Objects;

public record TerminalCandidateAudit(
        String entityId,
        RecommendationEntityType entityType,
        CandidateScoreStatus status,
        CandidateScoreNotApplicableReason notApplicableReason,
        CandidateScoreHardExclusionReason hardExclusionReason,
        String scorePolicyVersion
) {
    public TerminalCandidateAudit {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
    }
}
