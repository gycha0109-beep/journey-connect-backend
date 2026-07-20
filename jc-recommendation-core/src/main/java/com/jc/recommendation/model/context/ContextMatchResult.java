package com.jc.recommendation.model.context;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.List;
import java.util.Objects;

public record ContextMatchResult(
        String contextId,
        String entityId,
        RecommendationEntityType entityType,
        ContextMatchStatus status,
        Double score,
        Double baseScore,
        Double preferredCoverage,
        Double avoidanceCoverage,
        Double observedPreferredStrength,
        Double observedAvoidedStrength,
        List<String> acceptedSoftClauseIds,
        List<String> ignoredClauseIds,
        List<String> observedClauseIds,
        List<String> unknownClauseIds,
        List<String> matchedPreferredClauseIds,
        List<String> matchedAvoidedClauseIds,
        List<String> softUsableFeatureIds,
        List<String> ignoredEntityFeatureIds,
        ContextMatchNotApplicableReason notApplicableReason,
        String policyVersion,
        List<SoftContextClauseBreakdown> breakdown
) {
    public ContextMatchResult {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(status, "status");
        acceptedSoftClauseIds = List.copyOf(acceptedSoftClauseIds);
        ignoredClauseIds = List.copyOf(ignoredClauseIds);
        observedClauseIds = List.copyOf(observedClauseIds);
        unknownClauseIds = List.copyOf(unknownClauseIds);
        matchedPreferredClauseIds = List.copyOf(matchedPreferredClauseIds);
        matchedAvoidedClauseIds = List.copyOf(matchedAvoidedClauseIds);
        softUsableFeatureIds = List.copyOf(softUsableFeatureIds);
        ignoredEntityFeatureIds = List.copyOf(ignoredEntityFeatureIds);
        Objects.requireNonNull(policyVersion, "policyVersion");
        breakdown = List.copyOf(breakdown);
    }
}
