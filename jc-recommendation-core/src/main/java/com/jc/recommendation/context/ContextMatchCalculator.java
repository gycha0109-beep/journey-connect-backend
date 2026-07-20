package com.jc.recommendation.context;

import com.jc.recommendation.model.context.CalculateContextMatchInput;
import com.jc.recommendation.model.context.ContextClause;
import com.jc.recommendation.model.context.ContextClauseEnforcement;
import com.jc.recommendation.model.context.ContextEligibilityNotApplicableReason;
import com.jc.recommendation.model.context.ContextEligibilityResult;
import com.jc.recommendation.model.context.ContextEligibilityStatus;
import com.jc.recommendation.model.context.ContextMatchNotApplicableReason;
import com.jc.recommendation.model.context.ContextMatchResult;
import com.jc.recommendation.model.context.ContextMatchStatus;
import com.jc.recommendation.model.context.ContextScope;
import com.jc.recommendation.model.context.SoftContextClauseBreakdown;
import com.jc.recommendation.model.context.SoftContextClauseEvaluationStatus;
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

public final class ContextMatchCalculator {
    private record SoftUsableFeature(String featureId, FeatureGroup group, FeatureSource source, double weight) {
    }

    private record Resolution(Map<String, SoftUsableFeature> usable, List<String> ignoredFeatureIds) {
        private Resolution {
            usable = Map.copyOf(usable);
            ignoredFeatureIds = List.copyOf(ignoredFeatureIds);
        }
    }

    public ContextMatchResult calculate(CalculateContextMatchInput input) {
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
        ContextEligibilityResult eligibility = validateEligibility(input.eligibilityResult());
        if (!eligibility.contextId().equals(input.context().contextId())) {
            throw new IllegalArgumentException("eligibilityResult.contextId does not match context");
        }
        if (!eligibility.entityId().equals(input.entityId())) {
            throw new IllegalArgumentException("eligibilityResult.entityId does not match input");
        }
        if (eligibility.entityType() != input.entityType()) {
            throw new IllegalArgumentException("eligibilityResult.entityType does not match input");
        }
        if (!eligibility.policyVersion().equals(policy.policyVersion())) {
            throw new IllegalArgumentException("eligibilityResult.policyVersion does not match policy");
        }

        boolean expired = input.context().scope() == ContextScope.SESSION
                && input.context().expiresAt() != null
                && !referenceTime.isBefore(input.context().expiresAt());
        if (expired) {
            if (eligibility.status() != ContextEligibilityStatus.NOT_APPLICABLE
                    || eligibility.notApplicableReason() != ContextEligibilityNotApplicableReason.EXPIRED_CONTEXT) {
                throw new IllegalArgumentException("eligibilityResult expiry state conflicts with context time");
            }
            return empty(input, policy, ContextMatchNotApplicableReason.EXPIRED_CONTEXT,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        if (eligibility.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && eligibility.notApplicableReason() == ContextEligibilityNotApplicableReason.EXPIRED_CONTEXT) {
            throw new IllegalArgumentException("eligibilityResult reports expired_context for a nonexpired context");
        }

        boolean supported = policy.eligibleEntityTypes().contains(input.entityType());
        if (!supported) {
            if (eligibility.status() != ContextEligibilityStatus.NOT_APPLICABLE
                    || eligibility.notApplicableReason() != ContextEligibilityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
                throw new IllegalArgumentException("eligibilityResult support state conflicts with entity type");
            }
            return empty(input, policy, ContextMatchNotApplicableReason.UNSUPPORTED_ENTITY_TYPE,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        if (eligibility.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && eligibility.notApplicableReason() == ContextEligibilityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE) {
            throw new IllegalArgumentException("eligibilityResult reports unsupported_entity_type for a supported entity");
        }

        List<ContextClause> acceptedHard = input.context().clauses().stream()
                .filter(clause -> clause.enforcement().isHard())
                .filter(clause -> clause.validationStatus() == FeatureValidationStatus.ACCEPTED)
                .toList();
        if (acceptedHard.isEmpty()) {
            if (eligibility.status() != ContextEligibilityStatus.NOT_APPLICABLE
                    || eligibility.notApplicableReason() != ContextEligibilityNotApplicableReason.NO_HARD_CONTEXT_CLAUSES) {
                throw new IllegalArgumentException(
                        "eligibilityResult must report no_hard_context_clauses when no accepted hard clause exists");
            }
        } else if (eligibility.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && eligibility.notApplicableReason() == ContextEligibilityNotApplicableReason.NO_HARD_CONTEXT_CLAUSES) {
            throw new IllegalArgumentException(
                    "eligibilityResult reports no_hard_context_clauses despite accepted hard clauses");
        }

        if (eligibility.status() == ContextEligibilityStatus.HARD_EXCLUDED) {
            return empty(input, policy, ContextMatchNotApplicableReason.HARD_CONTEXT_NOT_ELIGIBLE,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        boolean permitsSoft = eligibility.status() == ContextEligibilityStatus.ELIGIBLE
                || (eligibility.status() == ContextEligibilityStatus.NOT_APPLICABLE
                && eligibility.notApplicableReason() == ContextEligibilityNotApplicableReason.NO_HARD_CONTEXT_CLAUSES);
        if (!permitsSoft) {
            throw new IllegalArgumentException("eligibilityResult does not permit soft Context calculation");
        }

        List<ContextClause> hardClauses = input.context().clauses().stream()
                .filter(clause -> clause.enforcement().isHard()).toList();
        List<ContextClause> softClauses = input.context().clauses().stream()
                .filter(clause -> clause.enforcement().isSoft()).toList();
        List<ContextClause> acceptedSoft = softClauses.stream()
                .filter(clause -> clause.validationStatus() == FeatureValidationStatus.ACCEPTED).toList();
        List<ContextClause> ignoredSoft = softClauses.stream()
                .filter(clause -> clause.validationStatus() != FeatureValidationStatus.ACCEPTED).toList();
        List<String> ignoredClauseIds = new ArrayList<>();
        hardClauses.forEach(clause -> ignoredClauseIds.add(clause.clauseId()));
        ignoredSoft.forEach(clause -> ignoredClauseIds.add(clause.clauseId()));
        ignoredClauseIds.sort(Utf16CodeUnitComparator.ASCENDING);
        List<SoftContextClauseBreakdown> ignoredBreakdown = ignoredSoft.stream()
                .map(ContextMatchCalculator::ignoredBreakdown)
                .sorted(Comparator.comparing(SoftContextClauseBreakdown::clauseId,
                        Utf16CodeUnitComparator.ASCENDING)).toList();

        if (acceptedSoft.isEmpty()) {
            return empty(input, policy, ContextMatchNotApplicableReason.NO_SOFT_CONTEXT_CLAUSES,
                    List.of(), ignoredClauseIds, List.of(), List.of(), List.of(), ignoredBreakdown);
        }

        List<EntityFeature> features = ContextContracts.validateEntityFeatures(
                input.entityFeatures(), input.entityId(), referenceTime);
        Resolution resolution = resolve(features, policy, sourcePolicy);
        List<SoftContextClauseBreakdown> evaluated = acceptedSoft.stream()
                .map(clause -> evaluateClause(clause, resolution.usable())).toList();
        List<SoftContextClauseBreakdown> breakdown = new ArrayList<>(evaluated);
        breakdown.addAll(ignoredBreakdown);
        breakdown.sort(Comparator.comparing(SoftContextClauseBreakdown::clauseId,
                Utf16CodeUnitComparator.ASCENDING));

        List<String> acceptedSoftIds = acceptedSoft.stream().map(ContextClause::clauseId)
                .sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<SoftContextClauseBreakdown> observed = evaluated.stream()
                .filter(SoftContextClauseBreakdown::denominatorIncluded).toList();
        List<String> unknownIds = evaluated.stream()
                .filter(item -> item.evaluationStatus() == SoftContextClauseEvaluationStatus.UNKNOWN)
                .map(SoftContextClauseBreakdown::clauseId).sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<String> observedIds = observed.stream().map(SoftContextClauseBreakdown::clauseId)
                .sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<String> matchedPreferred = evaluated.stream()
                .filter(item -> item.enforcement() == ContextClauseEnforcement.SOFT_PREFERRED
                        && item.evaluationStatus() == SoftContextClauseEvaluationStatus.MATCHED)
                .map(SoftContextClauseBreakdown::clauseId).sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<String> matchedAvoided = evaluated.stream()
                .filter(item -> item.enforcement() == ContextClauseEnforcement.SOFT_AVOIDED
                        && item.evaluationStatus() == SoftContextClauseEvaluationStatus.MATCHED)
                .map(SoftContextClauseBreakdown::clauseId).sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<String> usableIds = resolution.usable().keySet().stream()
                .sorted(Utf16CodeUnitComparator.ASCENDING).toList();

        if (observed.isEmpty()) {
            return empty(input, policy, ContextMatchNotApplicableReason.NO_OBSERVABLE_CONTEXT_GROUPS,
                    acceptedSoftIds, ignoredClauseIds, unknownIds, usableIds,
                    resolution.ignoredFeatureIds(), breakdown);
        }

        List<SoftContextClauseBreakdown> observedPreferred = observed.stream()
                .filter(item -> item.enforcement() == ContextClauseEnforcement.SOFT_PREFERRED).toList();
        List<SoftContextClauseBreakdown> observedAvoided = observed.stream()
                .filter(item -> item.enforcement() == ContextClauseEnforcement.SOFT_AVOIDED).toList();
        Double preferredStrength = observedPreferred.isEmpty() ? null
                : observedPreferred.stream().mapToDouble(SoftContextClauseBreakdown::strength).sum();
        Double avoidedStrength = observedAvoided.isEmpty() ? null
                : observedAvoided.stream().mapToDouble(SoftContextClauseBreakdown::strength).sum();
        double preferredContribution = observedPreferred.stream()
                .mapToDouble(item -> item.contribution() == null ? 0.0 : item.contribution()).sum();
        double avoidedContribution = observedAvoided.stream()
                .mapToDouble(item -> item.contribution() == null ? 0.0 : item.contribution()).sum();
        Double preferredCoverage = preferredStrength == null ? null : preferredContribution / preferredStrength;
        double avoidanceCoverage = avoidedStrength == null ? 0.0 : avoidedContribution / avoidedStrength;
        double baseScore = preferredCoverage == null ? 1.0 : preferredCoverage;
        double score = clamp(baseScore - avoidanceCoverage, policy.scoreMinimum(), policy.scoreMaximum());

        return new ContextMatchResult(
                input.context().contextId(), input.entityId(), input.entityType(), ContextMatchStatus.SCORED,
                score, baseScore, preferredCoverage, avoidanceCoverage, preferredStrength, avoidedStrength,
                acceptedSoftIds, ignoredClauseIds, observedIds, unknownIds, matchedPreferred, matchedAvoided,
                usableIds, resolution.ignoredFeatureIds(), null, policy.policyVersion(), breakdown
        );
    }

    private static ContextEligibilityResult validateEligibility(ContextEligibilityResult value) {
        if (value.status() == ContextEligibilityStatus.ELIGIBLE) {
            if (value.hardExclusionReason() != null || value.notApplicableReason() != null) {
                throw new IllegalArgumentException("eligible eligibilityResult must have null reasons");
            }
        } else if (value.status() == ContextEligibilityStatus.HARD_EXCLUDED) {
            if (value.hardExclusionReason() == null || value.notApplicableReason() != null) {
                throw new IllegalArgumentException("hard_excluded eligibilityResult has inconsistent reasons");
            }
        } else if (value.hardExclusionReason() != null || value.notApplicableReason() == null) {
            throw new IllegalArgumentException("not_applicable eligibilityResult has inconsistent reasons");
        }
        return value;
    }

    private static ContextMatchResult empty(
            CalculateContextMatchInput input,
            ContextPolicy policy,
            ContextMatchNotApplicableReason reason,
            List<String> acceptedSoftClauseIds,
            List<String> ignoredClauseIds,
            List<String> unknownClauseIds,
            List<String> softUsableFeatureIds,
            List<String> ignoredEntityFeatureIds,
            List<SoftContextClauseBreakdown> breakdown
    ) {
        return new ContextMatchResult(
                input.context().contextId(), input.entityId(), input.entityType(), ContextMatchStatus.NOT_APPLICABLE,
                null, null, null, null, null, null,
                acceptedSoftClauseIds, ignoredClauseIds, List.of(), unknownClauseIds,
                List.of(), List.of(), softUsableFeatureIds, ignoredEntityFeatureIds,
                reason, policy.policyVersion(), breakdown
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
        Map<String, SoftUsableFeature> usable = new HashMap<>();
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
            if (!policy.softEntityFeatureSources().contains(selected.source()) || selected.weight() <= 0.0) {
                ignored.add(entry.getKey());
                continue;
            }
            usable.put(entry.getKey(), new SoftUsableFeature(entry.getKey(),
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

    private static SoftContextClauseBreakdown ignoredBreakdown(ContextClause clause) {
        return new SoftContextClauseBreakdown(
                clause.clauseId(), clause.group(), clause.enforcement(), clause.matchMode(),
                ContextContracts.sorted(clause.featureIds()), clause.strength(), clause.source(),
                clause.validationStatus(), SoftContextClauseEvaluationStatus.IGNORED,
                false, List.of(), List.of(), null, null, false
        );
    }

    private static SoftContextClauseBreakdown evaluateClause(
            ContextClause clause,
            Map<String, SoftUsableFeature> usable
    ) {
        List<String> observedIds = usable.values().stream()
                .filter(feature -> feature.group() == clause.group())
                .map(SoftUsableFeature::featureId).sorted(Utf16CodeUnitComparator.ASCENDING).toList();
        List<String> featureIds = ContextContracts.sorted(clause.featureIds());
        if (observedIds.isEmpty()) {
            return new SoftContextClauseBreakdown(
                    clause.clauseId(), clause.group(), clause.enforcement(), clause.matchMode(), featureIds,
                    clause.strength(), clause.source(), clause.validationStatus(),
                    SoftContextClauseEvaluationStatus.UNKNOWN, false, List.of(), List.of(),
                    null, null, false
            );
        }
        Set<String> observedSet = Set.copyOf(observedIds);
        List<String> matchedIds = featureIds.stream().filter(observedSet::contains).toList();
        double matchQuality = 0.0;
        if (clause.matchMode() == com.jc.recommendation.model.context.ContextMatchMode.ANY) {
            for (String featureId : matchedIds) {
                SoftUsableFeature feature = usable.get(featureId);
                if (feature != null) {
                    matchQuality = Math.max(matchQuality, feature.weight());
                }
            }
        } else if (matchedIds.size() == featureIds.size()) {
            double total = 0.0;
            for (String featureId : featureIds) {
                SoftUsableFeature feature = usable.get(featureId);
                if (feature == null) {
                    throw new IllegalStateException("Resolved all-match feature disappeared: " + featureId);
                }
                total += feature.weight();
            }
            matchQuality = total / featureIds.size();
        }
        double contribution = clause.strength() * matchQuality;
        return new SoftContextClauseBreakdown(
                clause.clauseId(), clause.group(), clause.enforcement(), clause.matchMode(), featureIds,
                clause.strength(), clause.source(), clause.validationStatus(),
                matchQuality > 0.0 ? SoftContextClauseEvaluationStatus.MATCHED
                        : SoftContextClauseEvaluationStatus.NOT_MATCHED,
                true, observedIds, matchedIds, matchQuality, contribution, true
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }
}
