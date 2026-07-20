package com.jc.recommendation.interest;

import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.model.interest.CalculateInterestMatchInput;
import com.jc.recommendation.model.interest.InterestMatchFeatureBreakdown;
import com.jc.recommendation.model.interest.InterestMatchNotApplicableReason;
import com.jc.recommendation.model.interest.InterestMatchResult;
import com.jc.recommendation.model.interest.InterestMatchStatus;
import com.jc.recommendation.model.interest.UserInterestSignal;
import com.jc.recommendation.policy.FoundationPolicies;
import com.jc.recommendation.policy.InterestMatchPolicy;
import com.jc.recommendation.policy.ScoringPolicies;
import com.jc.recommendation.policy.SourcePriorityPolicy;
import com.jc.recommendation.support.Utf16CodeUnitComparator;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InterestMatchCalculator {
    private static final double EPSILON = 1.0e-12;

    private InterestMatchCalculator() {
    }

    public static InterestMatchResult calculate(CalculateInterestMatchInput input) {
        assertNonEmpty(input.userId(), "userId");
        assertNonEmpty(input.entityId(), "entityId");
        InterestMatchPolicy policy = input.policy() == null ? ScoringPolicies.INTEREST_MATCH_V1 : input.policy();
        SourcePriorityPolicy sourcePolicy = input.sourcePriorityPolicy() == null
                ? FoundationPolicies.SOURCE_PRIORITY_V1
                : input.sourcePriorityPolicy();
        validatePolicies(policy, sourcePolicy);

        Map<String, UserInterestSignal> signals = resolveUserSignals(input, sourcePolicy);
        ResolvedEntityFeatures entityFeatures = resolveEntityFeatures(input, sourcePolicy);

        if (signals.isEmpty()) {
            return makeNotApplicableResult(
                    input,
                    policy,
                    entityFeatures,
                    signals,
                    InterestMatchNotApplicableReason.NO_USER_INTEREST_SIGNALS
            );
        }
        if (entityFeatures.usable().isEmpty()) {
            return makeNotApplicableResult(
                    input,
                    policy,
                    entityFeatures,
                    signals,
                    InterestMatchNotApplicableReason.NO_USABLE_ENTITY_FEATURES
            );
        }

        List<InterestMatchFeatureBreakdown> breakdown = new ArrayList<>();
        Set<String> matchedPrefer = new HashSet<>();
        Set<String> matchedAvoid = new HashSet<>();
        Set<String> unmatched = new HashSet<>();
        Set<String> hardExclusions = new HashSet<>();
        double positiveContributionTotal = 0.0;
        double negativeContributionTotal = 0.0;
        double totalEntityFeatureWeight = entityFeatures.usable().stream()
                .mapToDouble(EntityFeature::weight)
                .sum();

        for (EntityFeature feature : entityFeatures.usable()) {
            UserInterestSignal signal = signals.get(feature.featureId());
            double positiveContribution = signal != null && signal.direction() == PreferenceKind.PREFER
                    ? feature.weight() * signal.strength()
                    : 0.0;
            double negativeContribution = signal != null && signal.direction() == PreferenceKind.AVOID
                    ? feature.weight() * signal.strength()
                    : 0.0;
            positiveContributionTotal += positiveContribution;
            negativeContributionTotal += negativeContribution;

            if (signal == null) {
                unmatched.add(feature.featureId());
            } else if (signal.direction() == PreferenceKind.PREFER) {
                matchedPrefer.add(feature.featureId());
            } else {
                matchedAvoid.add(feature.featureId());
                if (policy.hardAvoidSources().contains(signal.source())
                        && negativeContribution + EPSILON >= policy.hardAvoidContributionThreshold()) {
                    hardExclusions.add(feature.featureId());
                }
            }

            breakdown.add(new InterestMatchFeatureBreakdown(
                    feature.featureId(),
                    feature.weight(),
                    feature.source(),
                    signal == null ? null : signal.direction(),
                    signal == null ? null : signal.strength(),
                    signal == null ? null : signal.source(),
                    positiveContribution,
                    negativeContribution,
                    signal != null
            ));
        }

        double rawPositiveCoverage = positiveContributionTotal / totalEntityFeatureWeight;
        double rawNegativeCoverage = negativeContributionTotal / totalEntityFeatureWeight;
        if (rawPositiveCoverage < -EPSILON || rawPositiveCoverage > 1.0 + EPSILON
                || rawNegativeCoverage < -EPSILON || rawNegativeCoverage > 1.0 + EPSILON) {
            throw new IllegalArgumentException("Interest coverage must remain within 0..1");
        }
        double positiveCoverage = clamp(rawPositiveCoverage, 0.0, 1.0);
        double negativeCoverage = clamp(rawNegativeCoverage, 0.0, 1.0);
        InterestMatchStatus status = hardExclusions.isEmpty()
                ? InterestMatchStatus.SCORED
                : InterestMatchStatus.HARD_EXCLUDED;
        double score = status == InterestMatchStatus.HARD_EXCLUDED
                ? 0.0
                : clamp(positiveCoverage - negativeCoverage, policy.scoreMinimum(), policy.scoreMaximum());

        return new InterestMatchResult(
                input.userId(),
                input.entityId(),
                status,
                score,
                positiveCoverage,
                negativeCoverage,
                totalEntityFeatureWeight,
                entityFeatures.usable().stream().map(EntityFeature::featureId).toList(),
                sorted(matchedPrefer),
                sorted(matchedAvoid),
                sorted(unmatched),
                entityFeatures.ignoredFeatureIds(),
                sorted(hardExclusions),
                null,
                policy.policyVersion(),
                breakdown
        );
    }

    private static Map<String, UserInterestSignal> resolveUserSignals(
            CalculateInterestMatchInput input,
            SourcePriorityPolicy sourcePolicy
    ) {
        List<UserInterestSignal> explicitSignals = ExplicitInterestSignalBuilder.build(input.explicitPreferences());
        for (UserInterestSignal signal : explicitSignals) {
            if (!signal.userId().equals(input.userId())) {
                throw new IllegalArgumentException(
                        "Explicit preference belongs to another user: " + signal.userId()
                );
            }
        }

        List<UserInterestSignal> acceptedInferred = new ArrayList<>();
        for (UserInterestSignal signal : input.inferredSignals()) {
            assertNonEmpty(signal.signalId(), "Interest signal signalId");
            if (!signal.userId().equals(input.userId())) {
                throw new IllegalArgumentException(
                        "Interest signal belongs to another user: " + signal.signalId()
                );
            }
            if (signal.source() == FeatureSource.EXPLICIT) {
                throw new IllegalArgumentException(
                        "Inferred signal cannot use explicit source: " + signal.signalId()
                );
            }
            if (!FeatureVocabularyV1.isRegisteredFeature(signal.featureId())) {
                throw new IllegalArgumentException("Unknown feature ID: " + signal.featureId());
            }
            assertUnitInterval(signal.strength(), "Interest signal strength for " + signal.signalId());
            if (signal.validationStatus() == FeatureValidationStatus.ACCEPTED) {
                acceptedInferred.add(signal);
            }
        }

        Map<String, EnumMap<FeatureSource, UserInterestSignal>> grouped = new LinkedHashMap<>();
        List<UserInterestSignal> allSignals = new ArrayList<>(explicitSignals);
        allSignals.addAll(acceptedInferred);
        for (UserInterestSignal signal : allSignals) {
            EnumMap<FeatureSource, UserInterestSignal> bySource = grouped.computeIfAbsent(
                    signal.featureId(),
                    ignored -> new EnumMap<>(FeatureSource.class)
            );
            if (bySource.putIfAbsent(signal.source(), signal) != null) {
                throw new IllegalArgumentException(
                        "Duplicate usable interest signal for " + signal.featureId()
                                + " from " + signal.source().wireValue()
                );
            }
        }

        Map<String, UserInterestSignal> resolved = new HashMap<>();
        for (Map.Entry<String, EnumMap<FeatureSource, UserInterestSignal>> entry : grouped.entrySet()) {
            UserInterestSignal selected = entry.getValue().values().stream()
                    .min(Comparator.comparingInt(signal -> sourceRank(signal.source(), sourcePolicy)))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unable to resolve interest signal for " + entry.getKey()
                    ));
            resolved.put(entry.getKey(), selected);
        }
        return resolved;
    }

    private static ResolvedEntityFeatures resolveEntityFeatures(
            CalculateInterestMatchInput input,
            SourcePriorityPolicy sourcePolicy
    ) {
        Map<String, EnumMap<FeatureSource, EntityFeature>> grouped = new LinkedHashMap<>();
        Set<String> ignored = new HashSet<>();

        for (EntityFeature feature : input.entityFeatures()) {
            if (!feature.entityId().equals(input.entityId())) {
                throw new IllegalArgumentException(
                        "Entity feature belongs to another entity: " + feature.entityId()
                );
            }
            if (!FeatureVocabularyV1.isRegisteredFeature(feature.featureId())) {
                throw new IllegalArgumentException("Unknown feature ID: " + feature.featureId());
            }
            assertUnitInterval(feature.weight(), "Entity feature weight for " + feature.featureId());
            if (feature.confidence() != null) {
                assertUnitInterval(
                        feature.confidence(),
                        "Entity feature confidence for " + feature.featureId()
                );
            }

            EnumMap<FeatureSource, EntityFeature> bySource = grouped.computeIfAbsent(
                    feature.featureId(),
                    ignoredKey -> new EnumMap<>(FeatureSource.class)
            );
            if (bySource.putIfAbsent(feature.source(), feature) != null) {
                throw new IllegalArgumentException(
                        "Duplicate entity feature for " + feature.featureId()
                                + " from " + feature.source().wireValue()
                );
            }
        }

        List<EntityFeature> usable = new ArrayList<>();
        for (Map.Entry<String, EnumMap<FeatureSource, EntityFeature>> entry : grouped.entrySet()) {
            EntityFeature selected = entry.getValue().values().stream()
                    .filter(feature -> feature.validationStatus() == FeatureValidationStatus.ACCEPTED)
                    .filter(feature -> feature.weight() > 0.0)
                    .min(Comparator.comparingInt(feature -> sourceRank(feature.source(), sourcePolicy)))
                    .orElse(null);
            if (selected == null) {
                ignored.add(entry.getKey());
            } else {
                usable.add(selected);
            }
        }

        usable.sort(Comparator.comparing(EntityFeature::featureId, Utf16CodeUnitComparator.ASCENDING));
        return new ResolvedEntityFeatures(List.copyOf(usable), sorted(ignored));
    }

    private static InterestMatchResult makeNotApplicableResult(
            CalculateInterestMatchInput input,
            InterestMatchPolicy policy,
            ResolvedEntityFeatures entityFeatures,
            Map<String, UserInterestSignal> signals,
            InterestMatchNotApplicableReason reason
    ) {
        List<InterestMatchFeatureBreakdown> breakdown = entityFeatures.usable().stream()
                .map(feature -> {
                    UserInterestSignal signal = signals.get(feature.featureId());
                    return new InterestMatchFeatureBreakdown(
                            feature.featureId(),
                            feature.weight(),
                            feature.source(),
                            signal == null ? null : signal.direction(),
                            signal == null ? null : signal.strength(),
                            signal == null ? null : signal.source(),
                            0.0,
                            0.0,
                            signal != null
                    );
                })
                .toList();
        return new InterestMatchResult(
                input.userId(),
                input.entityId(),
                InterestMatchStatus.NOT_APPLICABLE,
                null,
                0.0,
                0.0,
                entityFeatures.usable().stream().mapToDouble(EntityFeature::weight).sum(),
                entityFeatures.usable().stream().map(EntityFeature::featureId).toList(),
                List.of(),
                List.of(),
                entityFeatures.usable().stream()
                        .filter(feature -> !signals.containsKey(feature.featureId()))
                        .map(EntityFeature::featureId)
                        .toList(),
                entityFeatures.ignoredFeatureIds(),
                List.of(),
                reason,
                policy.policyVersion(),
                breakdown
        );
    }

    private static void validatePolicies(
            InterestMatchPolicy policy,
            SourcePriorityPolicy sourcePolicy
    ) {
        assertUnitInterval(
                policy.hardAvoidContributionThreshold(),
                "hardAvoidContributionThreshold"
        );
        assertUnitInterval(policy.scoreMinimum(), "scoreMinimum");
        assertUnitInterval(policy.scoreMaximum(), "scoreMaximum");
        if (policy.scoreMinimum() > policy.scoreMaximum()) {
            throw new IllegalArgumentException("scoreMinimum cannot exceed scoreMaximum");
        }
        if (!policy.exactFeatureMatchOnly()) {
            throw new IllegalArgumentException("Phase 2.1 supports exact feature matching only");
        }
        if (new HashSet<>(sourcePolicy.priority()).size() != sourcePolicy.priority().size()) {
            throw new IllegalArgumentException("Source priority policy contains duplicate sources");
        }
    }

    private static int sourceRank(FeatureSource source, SourcePriorityPolicy policy) {
        int index = policy.priority().indexOf(source);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Source is missing from priority policy: " + source.wireValue()
            );
        }
        return index;
    }

    private static void assertNonEmpty(String value, String fieldName) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    private static void assertUnitInterval(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be a finite number within 0..1"
            );
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted(Utf16CodeUnitComparator.ASCENDING).toList();
    }

    private record ResolvedEntityFeatures(
            List<EntityFeature> usable,
            List<String> ignoredFeatureIds
    ) {
    }
}
