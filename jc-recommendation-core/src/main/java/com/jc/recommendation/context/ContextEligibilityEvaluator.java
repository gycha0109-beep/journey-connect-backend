package com.jc.recommendation.context;

import com.jc.recommendation.model.context.ContextClause;
import com.jc.recommendation.model.context.ContextClauseEnforcement;
import com.jc.recommendation.model.context.ContextEligibilityNotApplicableReason;
import com.jc.recommendation.model.context.ContextEligibilityResult;
import com.jc.recommendation.model.context.ContextEligibilityStatus;
import com.jc.recommendation.model.context.ContextHardExclusionReason;
import com.jc.recommendation.model.context.EvaluateContextEligibilityInput;
import com.jc.recommendation.model.context.HardContextClauseBreakdown;
import com.jc.recommendation.model.context.HardContextClauseEvaluationStatus;
import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.policy.ContextPolicy;
import com.jc.recommendation.policy.FoundationPolicies;
import com.jc.recommendation.policy.ScoringPolicies;
import com.jc.recommendation.policy.SourcePriorityPolicy;
import com.jc.recommendation.support.Utf16CodeUnitComparator;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContextEligibilityEvaluator {
    private record ResolvedFeature(String featureId, FeatureGroup group, FeatureSource source, double weight) {
    }

    private record Resolution(Map<String, ResolvedFeature> hardUsable, List<String> ignoredFeatureIds) {
        private Resolution {
            hardUsable = Map.copyOf(hardUsable);
            ignoredFeatureIds = List.copyOf(ignoredFeatureIds);
        }
    }

    public ContextEligibilityResult evaluate(EvaluateContextEligibilityInput input) {
        ContextPolicy policy = input.policy() == null ? ScoringPolicies.CONTEXT_MATCH_V1 : input.policy();
        SourcePriorityPolicy sourcePolicy = input.sourcePriorityPolicy() == null
                ? FoundationPolicies.SOURCE_PRIORITY_V1 : input.sourcePriorityPolicy();
        ContextContracts.validatePolicy(policy);
        ContextContracts.validateSourcePriorityPolicy(sourcePolicy);
        ContextContracts.validateContext(input.context(), policy);
        ContextContracts.requireNonBlank(input.entityId(), "entityId");

        Instant referenceTime = input.referenceTime();
        if (input.context().createdAt().isAfter(referenceTime)) {
            throw new IllegalArgumentException("context.createdAt must not be in the future");
        }
        if (input.context().scope() == com.jc.recommendation.model.context.ContextScope.SESSION) {
            Instant expiresAt = input.context().expiresAt();
            if (expiresAt == null) {
                throw new IllegalArgumentException("Session context expiresAt is required");
            }
            if (!referenceTime.isBefore(expiresAt)) {
                return notApplicable(input, policy, ContextEligibilityNotApplicableReason.EXPIRED_CONTEXT, List.of(), List.of());
            }
        }

        List<EntityFeature> features = ContextContracts.validateEntityFeatures(
                input.entityFeatures(), input.entityId(), referenceTime);
        if (!policy.eligibleEntityTypes().contains(input.entityType())) {
            return notApplicable(input, policy, ContextEligibilityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE, List.of(), List.of());
        }

        List<ContextClause> hardClauses = input.context().clauses().stream()
                .filter(clause -> clause.enforcement().isHard()).toList();
        List<ContextClause> acceptedHard = hardClauses.stream()
                .filter(clause -> clause.validationStatus() == FeatureValidationStatus.ACCEPTED).toList();
        List<ContextClause> ignoredHard = hardClauses.stream()
                .filter(clause -> clause.validationStatus() != FeatureValidationStatus.ACCEPTED).toList();
        List<ContextClause> softClauses = input.context().clauses().stream()
                .filter(clause -> clause.enforcement().isSoft()).toList();

        List<String> ignoredClauseIds = new ArrayList<>();
        ignoredHard.forEach(clause -> ignoredClauseIds.add(clause.clauseId()));
        softClauses.forEach(clause -> ignoredClauseIds.add(clause.clauseId()));
        ignoredClauseIds.sort(Utf16CodeUnitComparator.ASCENDING);
        List<HardContextClauseBreakdown> ignoredBreakdown = ignoredHard.stream()
                .map(ContextEligibilityEvaluator::ignoredBreakdown)
                .sorted(Comparator.comparing(HardContextClauseBreakdown::clauseId, Utf16CodeUnitComparator.ASCENDING))
                .toList();

        if (acceptedHard.isEmpty()) {
            return notApplicable(input, policy, ContextEligibilityNotApplicableReason.NO_HARD_CONTEXT_CLAUSES,
                    ignoredClauseIds, ignoredBreakdown);
        }

        Resolution resolution = resolve(features, policy, sourcePolicy);
        List<HardContextClauseBreakdown> evaluated = acceptedHard.stream()
                .map(clause -> evaluateClause(clause, resolution.hardUsable()))
                .sorted(Comparator.comparing(HardContextClauseBreakdown::clauseId, Utf16CodeUnitComparator.ASCENDING))
                .toList();
        List<HardContextClauseBreakdown> breakdown = new ArrayList<>(evaluated);
        breakdown.addAll(ignoredBreakdown);
        breakdown.sort(Comparator.comparing(HardContextClauseBreakdown::clauseId, Utf16CodeUnitComparator.ASCENDING));

        List<String> matchedExcluded = evaluated.stream()
                .filter(item -> item.enforcement() == ContextClauseEnforcement.HARD_EXCLUDED && item.exclusionTriggered())
                .map(HardContextClauseBreakdown::clauseId).sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<String> missingRequired = evaluated.stream()
                .filter(item -> item.enforcement() == ContextClauseEnforcement.HARD_REQUIRED
                        && Boolean.FALSE.equals(item.requiredSatisfied()))
                .map(HardContextClauseBreakdown::clauseId).sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        ContextHardExclusionReason reason = !matchedExcluded.isEmpty()
                ? ContextHardExclusionReason.MATCHED_EXCLUDED_CLAUSE
                : !missingRequired.isEmpty() ? ContextHardExclusionReason.MISSING_REQUIRED_CLAUSE : null;

        return new ContextEligibilityResult(
                input.context().contextId(), input.entityId(), input.entityType(),
                reason == null ? ContextEligibilityStatus.ELIGIBLE : ContextEligibilityStatus.HARD_EXCLUDED,
                reason, null,
                acceptedHard.stream().map(ContextClause::clauseId).sorted(Utf16CodeUnitComparator.ASCENDING).toList(),
                ignoredClauseIds, matchedExcluded, missingRequired,
                resolution.hardUsable().keySet().stream().sorted(Utf16CodeUnitComparator.ASCENDING).toList(),
                resolution.ignoredFeatureIds(), policy.policyVersion(), breakdown
        );
    }

    private static ContextEligibilityResult notApplicable(
            EvaluateContextEligibilityInput input,
            ContextPolicy policy,
            ContextEligibilityNotApplicableReason reason,
            List<String> ignoredClauseIds,
            List<HardContextClauseBreakdown> breakdown
    ) {
        return new ContextEligibilityResult(
                input.context().contextId(), input.entityId(), input.entityType(), ContextEligibilityStatus.NOT_APPLICABLE,
                null, reason, List.of(), ignoredClauseIds.stream().sorted(Utf16CodeUnitComparator.ASCENDING).toList(),
                List.of(), List.of(), List.of(), List.of(), policy.policyVersion(),
                breakdown.stream().sorted(Comparator.comparing(HardContextClauseBreakdown::clauseId,
                        Utf16CodeUnitComparator.ASCENDING)).toList()
        );
    }

    private static Resolution resolve(
            List<EntityFeature> features,
            ContextPolicy policy,
            SourcePriorityPolicy sourcePolicy
    ) {
        Map<String, List<EntityFeature>> grouped = new LinkedHashMap<>();
        for (EntityFeature feature : features) {
            grouped.computeIfAbsent(feature.featureId(), ignored -> new ArrayList<>()).add(feature);
        }
        Map<FeatureSource, Integer> ranks = new EnumMap<>(FeatureSource.class);
        for (int index = 0; index < sourcePolicy.priority().size(); index++) {
            ranks.put(sourcePolicy.priority().get(index), index);
        }
        Map<String, ResolvedFeature> usable = new HashMap<>();
        Set<String> ignored = new HashSet<>();
        for (Map.Entry<String, List<EntityFeature>> entry : grouped.entrySet()) {
            List<EntityFeature> accepted = entry.getValue().stream()
                    .filter(feature -> feature.validationStatus() == FeatureValidationStatus.ACCEPTED)
                    .sorted(Comparator.comparingInt(feature -> rank(ranks, feature.source())))
                    .toList();
            if (accepted.isEmpty()) {
                ignored.add(entry.getKey());
                continue;
            }
            EntityFeature selected = accepted.get(0);
            if (!policy.hardEntityFeatureSources().contains(selected.source())
                    || selected.weight() < policy.hardMinimumEntityFeatureWeight()) {
                ignored.add(entry.getKey());
                continue;
            }
            usable.put(entry.getKey(), new ResolvedFeature(entry.getKey(),
                    FeatureVocabularyV1.getFeatureById(entry.getKey()).group(), selected.source(), selected.weight()));
        }
        return new Resolution(usable, ignored.stream().sorted(Utf16CodeUnitComparator.ASCENDING).toList());
    }

    private static int rank(Map<FeatureSource, Integer> ranks, FeatureSource source) {
        Integer value = ranks.get(source);
        if (value == null) {
            throw new IllegalArgumentException("SourcePriorityPolicy is missing a feature source");
        }
        return value;
    }

    private static HardContextClauseBreakdown ignoredBreakdown(ContextClause clause) {
        return new HardContextClauseBreakdown(
                clause.clauseId(), clause.group(), clause.enforcement(), clause.matchMode(),
                ContextContracts.sorted(clause.featureIds()), clause.source(), clause.validationStatus(),
                HardContextClauseEvaluationStatus.IGNORED, false, List.of(), List.of(), null, false
        );
    }

    private static HardContextClauseBreakdown evaluateClause(
            ContextClause clause,
            Map<String, ResolvedFeature> hardUsable
    ) {
        List<String> observed = hardUsable.values().stream()
                .filter(feature -> feature.group() == clause.group())
                .map(ResolvedFeature::featureId).sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<String> clauseIds = ContextContracts.sorted(clause.featureIds());
        Set<String> observedSet = Set.copyOf(observed);
        List<String> matched = clauseIds.stream().filter(observedSet::contains).toList();
        boolean isMatched = clause.matchMode() == com.jc.recommendation.model.context.ContextMatchMode.ANY
                ? !matched.isEmpty() : matched.size() == clauseIds.size();
        boolean hardRequired = clause.enforcement() == ContextClauseEnforcement.HARD_REQUIRED;
        return new HardContextClauseBreakdown(
                clause.clauseId(), clause.group(), clause.enforcement(), clause.matchMode(), clauseIds,
                clause.source(), clause.validationStatus(),
                isMatched ? HardContextClauseEvaluationStatus.MATCHED : HardContextClauseEvaluationStatus.NOT_MATCHED,
                !observed.isEmpty(), matched, observed, hardRequired ? isMatched : null,
                !hardRequired && isMatched
        );
    }
}
