package com.jc.recommendation.model.context;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.List;
import java.util.Objects;

public record ContextEligibilityResult(
        String contextId,
        String entityId,
        RecommendationEntityType entityType,
        ContextEligibilityStatus status,
        ContextHardExclusionReason hardExclusionReason,
        ContextEligibilityNotApplicableReason notApplicableReason,
        List<String> acceptedHardClauseIds,
        List<String> ignoredClauseIds,
        List<String> matchedExcludedClauseIds,
        List<String> missingRequiredClauseIds,
        List<String> hardUsableFeatureIds,
        List<String> ignoredEntityFeatureIds,
        String policyVersion,
        List<HardContextClauseBreakdown> breakdown
) {
    public ContextEligibilityResult {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(status, "status");
        acceptedHardClauseIds = List.copyOf(acceptedHardClauseIds);
        ignoredClauseIds = List.copyOf(ignoredClauseIds);
        matchedExcludedClauseIds = List.copyOf(matchedExcludedClauseIds);
        missingRequiredClauseIds = List.copyOf(missingRequiredClauseIds);
        hardUsableFeatureIds = List.copyOf(hardUsableFeatureIds);
        ignoredEntityFeatureIds = List.copyOf(ignoredEntityFeatureIds);
        Objects.requireNonNull(policyVersion, "policyVersion");
        breakdown = List.copyOf(breakdown);
    }
}
