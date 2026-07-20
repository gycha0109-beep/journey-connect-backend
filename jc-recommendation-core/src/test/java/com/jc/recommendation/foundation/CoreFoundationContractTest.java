package com.jc.recommendation.foundation;

import com.jc.recommendation.decay.RepeatDecay;
import com.jc.recommendation.decay.TimeDecay;
import com.jc.recommendation.interest.ExplicitInterestSignalBuilder;
import com.jc.recommendation.limit.CandidateLimiter;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.ExplicitPreference;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.policy.CandidateLimitPolicies;
import com.jc.recommendation.policy.FoundationPolicies;
import com.jc.recommendation.policy.SaturationPolicy;
import com.jc.recommendation.saturation.ScoreSaturation;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CoreFoundationContractTest {
    private static final double TOLERANCE = 1.0e-12;

    private CoreFoundationContractTest() {
    }

    public static void main(String[] args) {
        candidateLimitPolicyMatchesTypeScriptV1();
        candidateLimiterMatchesSliceSemantics();
        featureVocabularyMatchesTypeScriptV1();
        entityFeatureUsabilityMatchesReferenceContract();
        foundationPoliciesMatchTypeScriptV1();
        repeatDecayMatchesReferenceContract();
        timeDecayMatchesReferenceContract();
        saturationMatchesReferenceContract();
        explicitInterestSignalsMatchReferenceContract();
        System.out.println("Java recommendation core foundation contract: PASS");
    }

    private static void candidateLimitPolicyMatchesTypeScriptV1() {
        var policy = CandidateLimitPolicies.V1;
        equal("candidate-limit-v1", policy.policyVersion(), "policyVersion");
        equal(Instant.parse("2026-07-01T00:00:00Z"), policy.effectiveFrom(), "effectiveFrom");
        equal(100, policy.maxCandidatesToScore(), "maxCandidatesToScore");
        equal(20, policy.defaultResultLimit(), "defaultResultLimit");
        equal(30, policy.hardResultLimit(), "hardResultLimit");
    }

    private static void candidateLimiterMatchesSliceSemantics() {
        List<Integer> candidates = new ArrayList<>();
        for (int value = 0; value < 150; value++) {
            candidates.add(value);
        }

        equal(0, CandidateLimiter.limitCandidates(List.of(), CandidateLimitPolicies.V1).size(), "empty input size");
        equal(50, CandidateLimiter.limitCandidates(candidates.subList(0, 50), CandidateLimitPolicies.V1).size(), "50 input size");

        List<Integer> policyLimited = CandidateLimiter.limitCandidates(candidates, CandidateLimitPolicies.V1);
        equal(100, policyLimited.size(), "policy limited size");
        equal(99, policyLimited.get(99), "policy limited last element");

        List<Integer> requestLimited = CandidateLimiter.limitCandidates(candidates, CandidateLimitPolicies.V1, 7);
        equal(List.of(0, 1, 2, 3, 4, 5, 6), requestLimited, "request limited values");
        expectUnsupported(() -> requestLimited.add(7));

        expectIllegalArgument(
                () -> CandidateLimiter.limitCandidates(candidates, CandidateLimitPolicies.V1, 0),
                "maxCandidates must be an integer greater than or equal to 1"
        );
    }

    private static void featureVocabularyMatchesTypeScriptV1() {
        equal(42, FeatureVocabularyV1.getAllFeatures().size(), "feature count");
        equal(10L, FeatureVocabularyV1.getAllFeatures().stream().map(item -> item.group()).distinct().count(), "group count");
        equal(5, FeatureVocabularyV1.getFeaturesByGroup(FeatureGroup.REGION).size(), "region count");
        isTrue(FeatureVocabularyV1.getAllFeatures().stream().allMatch(item -> item.id().equals(item.group().wireValue() + ":" + item.key())), "group:key ID format");
        equal("서울", FeatureVocabularyV1.getFeatureById("region:seoul").displayName(), "Seoul display name");
        equal(
                "theme:night_view",
                FeatureVocabularyV1.getFeatureByGroupAndKey(FeatureGroup.THEME, "night_view").id(),
                "group and key lookup"
        );
        expectIllegalArgument(
                () -> FeatureVocabularyV1.getFeatureById("region:unknown"),
                "Unknown feature ID: region:unknown"
        );
    }

    private static void entityFeatureUsabilityMatchesReferenceContract() {
        EntityFeature accepted = feature("region:seoul", FeatureSource.SYSTEM, FeatureValidationStatus.ACCEPTED);
        EntityFeature acceptedAi = feature("theme:cafe", FeatureSource.AI, FeatureValidationStatus.ACCEPTED);
        EntityFeature pending = feature("theme:cafe", FeatureSource.AI, FeatureValidationStatus.PENDING);
        EntityFeature rejected = feature("theme:cafe", FeatureSource.AI, FeatureValidationStatus.REJECTED);
        EntityFeature unknown = feature("region:unknown", FeatureSource.SYSTEM, FeatureValidationStatus.ACCEPTED);

        isTrue(FeatureVocabularyV1.isEntityFeatureUsable(accepted), "accepted registered feature");
        isTrue(FeatureVocabularyV1.isEntityFeatureUsable(acceptedAi), "accepted AI feature");
        isFalse(FeatureVocabularyV1.isEntityFeatureUsable(pending), "pending AI feature");
        isFalse(FeatureVocabularyV1.isEntityFeatureUsable(rejected), "rejected AI feature");
        isFalse(FeatureVocabularyV1.isEntityFeatureUsable(unknown), "unknown feature");
    }

    private static void foundationPoliciesMatchTypeScriptV1() {
        var policies = List.of(
                FoundationPolicies.EVENT_WEIGHT_V1,
                FoundationPolicies.REPEAT_DECAY_V1,
                FoundationPolicies.TIME_DECAY_V1,
                FoundationPolicies.SATURATION_V1,
                CandidateLimitPolicies.V1,
                FoundationPolicies.COLD_START_V1,
                FoundationPolicies.SOURCE_PRIORITY_V1
        );
        equal(
                List.of(
                        "event-weight-v1",
                        "repeat-decay-v1",
                        "time-decay-v1",
                        "saturation-v1",
                        "candidate-limit-v1",
                        "cold-start-v1",
                        "source-priority-v1"
                ),
                policies.stream().map(item -> item.policyVersion()).toList(),
                "policy version order"
        );
        isTrue(policies.stream().allMatch(item -> item.effectiveFrom().equals(Instant.parse("2026-07-01T00:00:00Z"))), "effectiveFrom");
        equal(null, FoundationPolicies.EVENT_WEIGHT_V1.weights().get(EventType.REPORT), "report weight");
        close(0.0, FoundationPolicies.EVENT_WEIGHT_V1.weights().get(EventType.IMPRESSION), "impression weight");
        close(1.0, FoundationPolicies.COLD_START_V1.explicitPreference().total(), "explicit cold-start total");
        close(1.0, FoundationPolicies.COLD_START_V1.emptyProfile().total(), "empty cold-start total");
        equal(
                List.of(FeatureSource.EXPLICIT, FeatureSource.ADMIN, FeatureSource.SYSTEM, FeatureSource.BEHAVIOR, FeatureSource.AI),
                FoundationPolicies.SOURCE_PRIORITY_V1.priority(),
                "source priority"
        );
    }

    private static void repeatDecayMatchesReferenceContract() {
        close(1.0, RepeatDecay.apply(1, 1, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1).decayedDelta(), "first repeat");
        close(0.3, RepeatDecay.apply(1, 2, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1).decayedDelta(), "second repeat");
        close(0.1, RepeatDecay.apply(1, 3, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1).decayedDelta(), "third repeat");
        close(
                RepeatDecay.apply(1, 3, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1).multiplier(),
                RepeatDecay.apply(1, 100, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1).multiplier(),
                "third and later multiplier"
        );
        double total = 0;
        for (int occurrence = 1; occurrence <= 100; occurrence++) {
            total += RepeatDecay.apply(1, occurrence, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1).decayedDelta();
        }
        close(11.1, total, "100 repeated views");
        expectIllegalArgumentContains(
                () -> RepeatDecay.apply(3, 1, EventType.LIKE, FoundationPolicies.REPEAT_DECAY_V1),
                "not supported"
        );
        expectIllegalArgumentContains(
                () -> RepeatDecay.apply(1, 0, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1),
                "occurrenceNumber"
        );
    }

    private static void timeDecayMatchesReferenceContract() {
        Instant reference = Instant.parse("2026-07-01T00:00:00Z");
        close(1.0, TimeDecay.apply(Instant.parse("2026-06-30T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1).multiplier(), "0-7 day multiplier");
        close(0.8, TimeDecay.apply(Instant.parse("2026-06-21T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1).multiplier(), "8-30 day multiplier");
        close(0.5, TimeDecay.apply(Instant.parse("2026-05-02T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1).multiplier(), "31-90 day multiplier");
        close(0.25, TimeDecay.apply(Instant.parse("2026-03-03T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1).multiplier(), "91+ day multiplier");
        equal("days-0-7", TimeDecay.apply(Instant.parse("2026-06-24T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1).bucketId(), "7 day boundary");
        equal("days-8-30", TimeDecay.apply(Instant.parse("2026-06-01T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1).bucketId(), "30 day boundary");
        equal("days-31-90", TimeDecay.apply(Instant.parse("2026-04-02T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1).bucketId(), "90 day boundary");
        expectIllegalArgumentContains(
                () -> TimeDecay.apply(Instant.parse("2026-07-02T00:00:00Z"), reference, FoundationPolicies.TIME_DECAY_V1),
                "later"
        );
    }

    private static void saturationMatchesReferenceContract() {
        close(0.0, ScoreSaturation.saturate(0, FoundationPolicies.SATURATION_V1), "zero saturation");
        close(0.0, ScoreSaturation.saturate(-5, FoundationPolicies.SATURATION_V1), "negative saturation");
        double one = ScoreSaturation.saturate(1, FoundationPolicies.SATURATION_V1);
        double five = ScoreSaturation.saturate(5, FoundationPolicies.SATURATION_V1);
        double ten = ScoreSaturation.saturate(10, FoundationPolicies.SATURATION_V1);
        double hundred = ScoreSaturation.saturate(100, FoundationPolicies.SATURATION_V1);
        isTrue(one > 0 && one < five && five < ten && ten < hundred && hundred <= 1, "monotonic saturation");
        double lowGrowth = ten - five;
        double highGrowth = ScoreSaturation.saturate(100, FoundationPolicies.SATURATION_V1)
                - ScoreSaturation.saturate(95, FoundationPolicies.SATURATION_V1);
        isTrue(highGrowth < lowGrowth, "reduced marginal growth");
        expectIllegalArgumentContains(() -> ScoreSaturation.saturate(Double.NaN, FoundationPolicies.SATURATION_V1), "finite");
        expectIllegalArgumentContains(
                () -> ScoreSaturation.saturate(1, new SaturationPolicy("saturation-v1", Instant.parse("2026-07-01T00:00:00Z"), 0)),
                "positive"
        );
        expectIllegalArgumentContains(() -> ScoreSaturation.saturate(Double.POSITIVE_INFINITY, FoundationPolicies.SATURATION_V1), "finite");
    }

    private static void explicitInterestSignalsMatchReferenceContract() {
        var preferences = List.of(
                preference("user-b", "theme:cafe", PreferenceKind.PREFER, 0.8),
                preference("user-a", "region:seoul", PreferenceKind.AVOID, 0.4)
        );
        var signals = ExplicitInterestSignalBuilder.build(preferences);
        equal(2, signals.size(), "explicit signal count");
        equal("explicit:user-a:region:seoul", signals.get(0).signalId(), "deterministic signal sorting");
        equal(FeatureSource.EXPLICIT, signals.get(0).source(), "explicit source");
        equal(FeatureValidationStatus.ACCEPTED, signals.get(0).validationStatus(), "accepted validation");
        expectIllegalArgumentContains(
                () -> ExplicitInterestSignalBuilder.build(List.of(preference("user", "theme:unknown", PreferenceKind.PREFER, 1))),
                "Unknown feature ID"
        );
        expectIllegalArgumentContains(
                () -> ExplicitInterestSignalBuilder.build(List.of(preference("user", "theme:cafe", PreferenceKind.PREFER, 1.1))),
                "within 0..1"
        );
        expectIllegalArgumentContains(
                () -> ExplicitInterestSignalBuilder.build(List.of(
                        preference("user", "theme:cafe", PreferenceKind.PREFER, 0.5),
                        preference("user", "theme:cafe", PreferenceKind.AVOID, 0.5)
                )),
                "Duplicate explicit preference"
        );
    }

    private static ExplicitPreference preference(String userId, String featureId, PreferenceKind kind, double strength) {
        return new ExplicitPreference(userId, featureId, kind, strength, Instant.parse("2026-07-01T00:00:00Z"));
    }

    private static EntityFeature feature(
            String featureId,
            FeatureSource source,
            FeatureValidationStatus validationStatus
    ) {
        return new EntityFeature(
                "post:1",
                featureId,
                1.0,
                null,
                source,
                validationStatus,
                Instant.parse("2026-07-17T00:00:00Z")
        );
    }

    private static void expectIllegalArgument(Runnable runnable, String expectedMessage) {
        try {
            runnable.run();
            throw new AssertionError("Expected IllegalArgumentException: " + expectedMessage);
        } catch (IllegalArgumentException exception) {
            equal(expectedMessage, exception.getMessage(), "exception message");
        }
    }

    private static void expectIllegalArgumentContains(Runnable runnable, String expectedMessagePart) {
        try {
            runnable.run();
            throw new AssertionError("Expected IllegalArgumentException containing: " + expectedMessagePart);
        } catch (IllegalArgumentException exception) {
            isTrue(exception.getMessage() != null && exception.getMessage().contains(expectedMessagePart), "exception message contains " + expectedMessagePart);
        }
    }

    private static void expectUnsupported(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void close(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void isTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label + " expected true");
        }
    }

    private static void isFalse(boolean value, String label) {
        if (value) {
            throw new AssertionError(label + " expected false");
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
