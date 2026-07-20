package com.jc.recommendation.model.interest;

import java.util.List;
import java.util.Objects;

public record InterestMatchResult(
        String userId,
        String entityId,
        InterestMatchStatus status,
        Double score,
        double positiveCoverage,
        double negativeCoverage,
        double totalEntityFeatureWeight,
        List<String> consideredFeatureIds,
        List<String> matchedPreferFeatureIds,
        List<String> matchedAvoidFeatureIds,
        List<String> unmatchedEntityFeatureIds,
        List<String> ignoredEntityFeatureIds,
        List<String> hardExclusionFeatureIds,
        InterestMatchNotApplicableReason notApplicableReason,
        String policyVersion,
        List<InterestMatchFeatureBreakdown> breakdown
) {
    public InterestMatchResult {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(status, "status");
        consideredFeatureIds = List.copyOf(consideredFeatureIds);
        matchedPreferFeatureIds = List.copyOf(matchedPreferFeatureIds);
        matchedAvoidFeatureIds = List.copyOf(matchedAvoidFeatureIds);
        unmatchedEntityFeatureIds = List.copyOf(unmatchedEntityFeatureIds);
        ignoredEntityFeatureIds = List.copyOf(ignoredEntityFeatureIds);
        hardExclusionFeatureIds = List.copyOf(hardExclusionFeatureIds);
        Objects.requireNonNull(policyVersion, "policyVersion");
        breakdown = List.copyOf(breakdown);
    }
}
