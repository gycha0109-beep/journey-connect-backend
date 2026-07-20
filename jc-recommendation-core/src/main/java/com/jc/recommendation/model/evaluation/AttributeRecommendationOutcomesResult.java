package com.jc.recommendation.model.evaluation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AttributeRecommendationOutcomesResult(
        String caseId, String recommendationRunId, String replayKey, int resolvedBehaviorEventCount,
        int attributedOutcomeEventCount, int attributedNumericEventCount, double associatedOutcomeValue,
        int clickCount, int positiveEventCount, int negativeEventCount, int severeReportCount,
        int ambiguousOutcomeEventCount, int unmatchedOutcomeEventCount, int runUserSessionMismatchCount,
        Map<RecommendationAttributionAuditCategory, Integer> auditCounts,
        List<RecommendationOutcomeAttribution> attributions,
        List<RecommendationOutcomeAttributionAudit> audits
) {
    public AttributeRecommendationOutcomesResult {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(recommendationRunId, "recommendationRunId");
        Objects.requireNonNull(replayKey, "replayKey");
        EnumMap<RecommendationAttributionAuditCategory, Integer> copy =
                new EnumMap<>(RecommendationAttributionAuditCategory.class);
        copy.putAll(auditCounts);
        auditCounts = Collections.unmodifiableMap(copy);
        attributions = List.copyOf(attributions);
        audits = List.copyOf(audits);
    }
}
