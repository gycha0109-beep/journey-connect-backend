package com.jc.recommendation.policy;

import com.jc.recommendation.model.context.ContextClauseSource;
import com.jc.recommendation.model.context.ContextSchemaVersion;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ContextPolicy(
        String policyVersion,
        Instant effectiveFrom,
        ContextSchemaVersion schemaVersion,
        List<RecommendationEntityType> eligibleEntityTypes,
        List<FeatureGroup> hardRequiredGroups,
        List<FeatureGroup> hardExcludedGroups,
        List<ContextClauseSource> hardClauseSources,
        List<FeatureSource> hardEntityFeatureSources,
        double hardMinimumEntityFeatureWeight,
        List<FeatureGroup> softAllowedGroups,
        List<ContextClauseSource> softClauseSources,
        List<FeatureSource> softEntityFeatureSources,
        boolean exactFeatureMatchOnly,
        long maxSessionLifetimeMilliseconds,
        long millisecondsPerDay,
        double scoreMinimum,
        double scoreMaximum
) implements VersionedPolicy {
    public ContextPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        eligibleEntityTypes = List.copyOf(eligibleEntityTypes);
        hardRequiredGroups = List.copyOf(hardRequiredGroups);
        hardExcludedGroups = List.copyOf(hardExcludedGroups);
        hardClauseSources = List.copyOf(hardClauseSources);
        hardEntityFeatureSources = List.copyOf(hardEntityFeatureSources);
        softAllowedGroups = List.copyOf(softAllowedGroups);
        softClauseSources = List.copyOf(softClauseSources);
        softEntityFeatureSources = List.copyOf(softEntityFeatureSources);
    }
}
