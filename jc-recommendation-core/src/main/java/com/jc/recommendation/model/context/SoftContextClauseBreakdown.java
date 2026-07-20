package com.jc.recommendation.model.context;

import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureValidationStatus;

import java.util.List;
import java.util.Objects;

public record SoftContextClauseBreakdown(
        String clauseId,
        FeatureGroup group,
        ContextClauseEnforcement enforcement,
        ContextMatchMode matchMode,
        List<String> featureIds,
        double strength,
        ContextClauseSource source,
        FeatureValidationStatus validationStatus,
        SoftContextClauseEvaluationStatus evaluationStatus,
        boolean observedGroup,
        List<String> observedSoftFeatureIds,
        List<String> matchedFeatureIds,
        Double matchQuality,
        Double contribution,
        boolean denominatorIncluded
) {
    public SoftContextClauseBreakdown {
        Objects.requireNonNull(clauseId, "clauseId");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(enforcement, "enforcement");
        Objects.requireNonNull(matchMode, "matchMode");
        featureIds = List.copyOf(featureIds);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(validationStatus, "validationStatus");
        Objects.requireNonNull(evaluationStatus, "evaluationStatus");
        observedSoftFeatureIds = List.copyOf(observedSoftFeatureIds);
        matchedFeatureIds = List.copyOf(matchedFeatureIds);
    }
}
