package com.jc.recommendation.context;

import com.jc.recommendation.model.context.ContextClause;
import com.jc.recommendation.model.context.ContextClauseEnforcement;
import com.jc.recommendation.model.context.ContextScope;
import com.jc.recommendation.model.context.ContextSchemaVersion;
import com.jc.recommendation.model.context.RecommendationContext;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.FeatureDefinition;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureStatus;
import com.jc.recommendation.policy.ContextPolicy;
import com.jc.recommendation.policy.SourcePriorityPolicy;
import com.jc.recommendation.support.Utf16CodeUnitComparator;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ContextContracts {
    private static final List<FeatureGroup> ALL_FEATURE_GROUPS = List.of(FeatureGroup.values());
    private static final Set<FeatureGroup> HARD_REQUIRED_ALLOWED = EnumSet.of(
            FeatureGroup.REGION,
            FeatureGroup.ENVIRONMENT,
            FeatureGroup.TRANSPORT
    );
    private static final Set<com.jc.recommendation.model.context.ContextClauseSource> HARD_SOURCE_ALLOWED = EnumSet.of(
            com.jc.recommendation.model.context.ContextClauseSource.EXPLICIT,
            com.jc.recommendation.model.context.ContextClauseSource.VALIDATED_QUERY
    );
    private static final Set<FeatureSource> HARD_ENTITY_SOURCE_ALLOWED = EnumSet.of(
            FeatureSource.EXPLICIT,
            FeatureSource.ADMIN,
            FeatureSource.SYSTEM
    );

    private ContextContracts() {
    }

    public static void validatePolicy(ContextPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        requireNonBlank(policy.policyVersion(), "ContextPolicy.policyVersion");
        Objects.requireNonNull(policy.effectiveFrom(), "ContextPolicy.effectiveFrom");
        if (policy.schemaVersion() != ContextSchemaVersion.V1) {
            throw new IllegalArgumentException("ContextPolicy.schemaVersion must equal context-v1");
        }
        requireDistinct(policy.eligibleEntityTypes(), "ContextPolicy.eligibleEntityTypes");
        if (policy.eligibleEntityTypes().contains(RecommendationEntityType.USER)) {
            throw new IllegalArgumentException("ContextPolicy must not support user entities");
        }
        requireDistinct(policy.hardRequiredGroups(), "ContextPolicy.hardRequiredGroups");
        requireSubset(policy.hardRequiredGroups(), HARD_REQUIRED_ALLOWED, "ContextPolicy.hardRequiredGroups");
        requireDistinct(policy.hardExcludedGroups(), "ContextPolicy.hardExcludedGroups");
        requireSubset(policy.hardRequiredGroups(), Set.copyOf(policy.hardExcludedGroups()), "ContextPolicy hard required groups");
        requireDistinct(policy.hardClauseSources(), "ContextPolicy.hardClauseSources");
        requireSubset(policy.hardClauseSources(), HARD_SOURCE_ALLOWED, "ContextPolicy.hardClauseSources");
        requireDistinct(policy.hardEntityFeatureSources(), "ContextPolicy.hardEntityFeatureSources");
        requireSubset(policy.hardEntityFeatureSources(), HARD_ENTITY_SOURCE_ALLOWED, "ContextPolicy.hardEntityFeatureSources");
        requireFiniteRange(policy.hardMinimumEntityFeatureWeight(), 0.0, 1.0, "ContextPolicy.hardMinimumEntityFeatureWeight");
        requireDistinct(policy.softAllowedGroups(), "ContextPolicy.softAllowedGroups");
        if (policy.softAllowedGroups().size() != ALL_FEATURE_GROUPS.size()
                || !Set.copyOf(policy.softAllowedGroups()).equals(Set.copyOf(ALL_FEATURE_GROUPS))) {
            throw new IllegalArgumentException("ContextPolicy.softAllowedGroups must include all feature groups");
        }
        requireDistinct(policy.softClauseSources(), "ContextPolicy.softClauseSources");
        requireDistinct(policy.softEntityFeatureSources(), "ContextPolicy.softEntityFeatureSources");
        requireSubset(policy.hardEntityFeatureSources(), Set.copyOf(policy.softEntityFeatureSources()), "ContextPolicy hard entity sources");
        if (!policy.exactFeatureMatchOnly()) {
            throw new IllegalArgumentException("ContextPolicy.exactFeatureMatchOnly must be true");
        }
        if (policy.maxSessionLifetimeMilliseconds() <= 0) {
            throw new IllegalArgumentException("ContextPolicy.maxSessionLifetimeMilliseconds must be a positive safe integer");
        }
        if (policy.millisecondsPerDay() != 86_400_000L) {
            throw new IllegalArgumentException("ContextPolicy.millisecondsPerDay must equal 86400000");
        }
        if (Double.compare(policy.scoreMinimum(), 0.0) != 0 || Double.compare(policy.scoreMaximum(), 1.0) != 0) {
            throw new IllegalArgumentException("ContextPolicy score range must be exactly 0..1");
        }
    }

    public static void validateSourcePriorityPolicy(SourcePriorityPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        requireNonBlank(policy.policyVersion(), "SourcePriorityPolicy.policyVersion");
        Objects.requireNonNull(policy.effectiveFrom(), "SourcePriorityPolicy.effectiveFrom");
        requireDistinct(policy.priority(), "SourcePriorityPolicy.priority");
        if (policy.priority().size() != FeatureSource.values().length
                || !Set.copyOf(policy.priority()).equals(EnumSet.allOf(FeatureSource.class))) {
            throw new IllegalArgumentException("SourcePriorityPolicy.priority must include every feature source exactly once");
        }
    }

    public static void validateContext(RecommendationContext context, ContextPolicy policy) {
        validatePolicy(policy);
        Objects.requireNonNull(context, "context");
        requireNonBlank(context.contextId(), "context.contextId");
        if (context.schemaVersion() != ContextSchemaVersion.V1) {
            throw new IllegalArgumentException("context.schemaVersion must equal context-v1");
        }
        if (context.scope() == ContextScope.REQUEST) {
            if (context.expiresAt() != null) {
                throw new IllegalArgumentException("Request context expiresAt must be null");
            }
        } else {
            if (context.expiresAt() == null) {
                throw new IllegalArgumentException("Session context expiresAt is required");
            }
            long lifetime;
            try {
                lifetime = Duration.between(context.createdAt(), context.expiresAt()).toMillis();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Session context lifetime exceeds policy maximum", exception);
            }
            if (lifetime <= 0) {
                throw new IllegalArgumentException("Session context expiresAt must be later than createdAt");
            }
            if (lifetime > policy.maxSessionLifetimeMilliseconds()) {
                throw new IllegalArgumentException("Session context lifetime exceeds policy maximum");
            }
        }

        Set<String> clauseIds = new HashSet<>();
        Set<String> normalizedKeys = new HashSet<>();
        java.util.Map<String, String> globallyUsed = new java.util.HashMap<>();
        for (int index = 0; index < context.clauses().size(); index++) {
            ContextClause clause = context.clauses().get(index);
            validateClause(clause, index, policy);
            if (!clauseIds.add(clause.clauseId())) {
                throw new IllegalArgumentException("Duplicate clauseId: " + clause.clauseId());
            }
            List<String> sortedIds = sorted(clause.featureIds());
            String normalized = clause.group().wireValue() + "|" + String.join(",", sortedIds)
                    + "|" + clause.enforcement().wireValue() + "|" + clause.matchMode().wireValue();
            if (!normalizedKeys.add(normalized)) {
                throw new IllegalArgumentException("Duplicate normalized clause: " + clause.clauseId());
            }
            for (String featureId : clause.featureIds()) {
                String previous = globallyUsed.putIfAbsent(featureId, clause.clauseId());
                if (previous != null) {
                    throw new IllegalArgumentException("Feature " + featureId + " appears in multiple clauses: "
                            + previous + ", " + clause.clauseId());
                }
            }
        }
    }

    public static List<EntityFeature> validateEntityFeatures(
            List<EntityFeature> features,
            String entityId,
            Instant referenceTime
    ) {
        Objects.requireNonNull(features, "features");
        requireNonBlank(entityId, "entityId");
        Objects.requireNonNull(referenceTime, "referenceTime");
        Set<String> duplicates = new HashSet<>();
        List<EntityFeature> result = new ArrayList<>(features.size());
        for (int index = 0; index < features.size(); index++) {
            EntityFeature feature = Objects.requireNonNull(features.get(index), "entityFeatures[" + index + "]");
            requireNonBlank(feature.entityId(), "entityFeatures[" + index + "].entityId");
            if (!feature.entityId().equals(entityId)) {
                throw new IllegalArgumentException("entityFeatures[" + index + "].entityId does not match input entityId");
            }
            requireNonBlank(feature.featureId(), "entityFeatures[" + index + "].featureId");
            FeatureDefinition definition = FeatureVocabularyV1.getFeatureById(feature.featureId());
            if (definition.status() != FeatureStatus.ACTIVE) {
                throw new IllegalArgumentException("Inactive feature is not allowed: " + feature.featureId());
            }
            requireFiniteRange(feature.weight(), 0.0, 1.0, "entityFeatures[" + index + "].weight");
            if (feature.confidence() != null) {
                requireFiniteRange(feature.confidence(), 0.0, 1.0, "entityFeatures[" + index + "].confidence");
            }
            if (feature.updatedAt().isAfter(referenceTime)) {
                throw new IllegalArgumentException("entityFeatures[" + index + "].updatedAt must not be in the future");
            }
            String duplicateKey = feature.featureId() + "|" + feature.source().wireValue();
            if (!duplicates.add(duplicateKey)) {
                throw new IllegalArgumentException("Duplicate entity feature source: " + duplicateKey);
            }
            result.add(feature);
        }
        return List.copyOf(result);
    }

    public static List<String> sorted(List<String> values) {
        return values.stream().sorted(Utf16CodeUnitComparator.ASCENDING).toList();
    }

    public static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    public static void requireFiniteRange(double value, double minimum, double maximum, String fieldName) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + " must be finite and within " + minimum + ".." + maximum);
        }
    }

    private static void validateClause(ContextClause clause, int index, ContextPolicy policy) {
        Objects.requireNonNull(clause, "clauses[" + index + "]");
        requireNonBlank(clause.clauseId(), "clauses[" + index + "].clauseId");
        if (clause.featureIds().isEmpty()) {
            throw new IllegalArgumentException("clauses[" + index + "].featureIds must not be empty");
        }
        Set<String> ids = new HashSet<>();
        for (String featureId : clause.featureIds()) {
            requireNonBlank(featureId, "clauses[" + index + "].featureIds item");
            FeatureDefinition definition = FeatureVocabularyV1.getFeatureById(featureId);
            if (definition.status() != FeatureStatus.ACTIVE) {
                throw new IllegalArgumentException("Inactive feature is not allowed: " + featureId);
            }
            if (definition.group() != clause.group()) {
                throw new IllegalArgumentException("Feature group mismatch for " + featureId);
            }
            if (!ids.add(featureId)) {
                throw new IllegalArgumentException("clauses[" + index + "].featureIds must not contain duplicates");
            }
        }
        requireFiniteRange(clause.strength(), 0.0, 1.0, "clauses[" + index + "].strength");
        if (clause.enforcement().isHard()) {
            if (!policy.hardClauseSources().contains(clause.source())) {
                throw new IllegalArgumentException("Hard clause source is not allowed: " + clause.source().wireValue());
            }
            if (Double.compare(clause.strength(), 1.0) != 0) {
                throw new IllegalArgumentException("Hard clause strength must equal 1");
            }
            List<FeatureGroup> allowedGroups = clause.enforcement() == ContextClauseEnforcement.HARD_REQUIRED
                    ? policy.hardRequiredGroups() : policy.hardExcludedGroups();
            if (!allowedGroups.contains(clause.group())) {
                throw new IllegalArgumentException(clause.group().wireValue() + " is not allowed for "
                        + clause.enforcement().wireValue());
            }
        } else {
            if (!policy.softAllowedGroups().contains(clause.group())) {
                throw new IllegalArgumentException(clause.group().wireValue() + " is not allowed for soft context");
            }
            if (!policy.softClauseSources().contains(clause.source())) {
                throw new IllegalArgumentException("Soft clause source is not allowed: " + clause.source().wireValue());
            }
            if (clause.strength() <= 0) {
                throw new IllegalArgumentException("Soft clause strength must be greater than 0");
            }
        }
    }

    private static <T> void requireDistinct(List<T> values, String fieldName) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(fieldName + " must not contain duplicates");
        }
    }

    private static <T> void requireSubset(List<T> values, Set<T> container, String fieldName) {
        for (T value : values) {
            if (!container.contains(value)) {
                throw new IllegalArgumentException(fieldName + " contains disallowed value: " + value);
            }
        }
    }
}
