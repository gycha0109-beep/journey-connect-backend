package com.jc.recommendation.model.context;

import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureValidationStatus;

import java.util.List;
import java.util.Objects;

public record HardContextClauseBreakdown(
        String clauseId,
        FeatureGroup group,
        ContextClauseEnforcement enforcement,
        ContextMatchMode matchMode,
        List<String> featureIds,
        ContextClauseSource source,
        FeatureValidationStatus validationStatus,
        HardContextClauseEvaluationStatus evaluationStatus,
        boolean observedGroup,
        List<String> matchedFeatureIds,
        List<String> observedHardFeatureIds,
        Boolean requiredSatisfied,
        boolean exclusionTriggered
) {
    public HardContextClauseBreakdown {
        Objects.requireNonNull(clauseId, "clauseId");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(enforcement, "enforcement");
        Objects.requireNonNull(matchMode, "matchMode");
        featureIds = List.copyOf(featureIds);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(validationStatus, "validationStatus");
        Objects.requireNonNull(evaluationStatus, "evaluationStatus");
        matchedFeatureIds = List.copyOf(matchedFeatureIds);
        observedHardFeatureIds = List.copyOf(observedHardFeatureIds);
    }
}
