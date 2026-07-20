package com.jc.recommendation.model.context;

import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureValidationStatus;

import java.util.List;
import java.util.Objects;

public record ContextClause(
        String clauseId,
        FeatureGroup group,
        List<String> featureIds,
        ContextClauseEnforcement enforcement,
        ContextMatchMode matchMode,
        double strength,
        ContextClauseSource source,
        FeatureValidationStatus validationStatus
) {
    public ContextClause {
        Objects.requireNonNull(clauseId, "clauseId");
        Objects.requireNonNull(group, "group");
        featureIds = List.copyOf(featureIds);
        Objects.requireNonNull(enforcement, "enforcement");
        Objects.requireNonNull(matchMode, "matchMode");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(validationStatus, "validationStatus");
    }
}
