package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.decay.RepeatDecay;
import com.jc.recommendation.decay.TimeDecay;
import com.jc.recommendation.interest.ExplicitInterestSignalBuilder;
import com.jc.recommendation.limit.CandidateLimiter;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import com.jc.recommendation.model.feature.ExplicitPreference;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.policy.CandidateLimitPolicies;
import com.jc.recommendation.policy.FoundationPolicies;
import com.jc.recommendation.policy.ScoringPolicies;
import com.jc.recommendation.policy.VersionedPolicy;
import com.jc.recommendation.saturation.ScoreSaturation;
import com.jc.recommendation.state.StateEventResolver;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CoreWave1GoldenOracle {
    private final List<Map<String, Object>> records = new ArrayList<>();

    public static void main(String[] args) {
        new CoreWave1GoldenOracle().run();
    }

    private void run() {
        policies();
        eventWeights();
        features();
        foundationContracts();
        scoringContracts();
        explicitSignals();
        stateEvents();

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "foundation-wave1-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 1 golden oracle", exception);
        }
    }

    private void policies() {
        List<VersionedPolicy> policies = List.of(
                FoundationPolicies.EVENT_WEIGHT_V1,
                FoundationPolicies.REPEAT_DECAY_V1,
                FoundationPolicies.TIME_DECAY_V1,
                FoundationPolicies.SATURATION_V1,
                CandidateLimitPolicies.V1,
                FoundationPolicies.COLD_START_V1,
                FoundationPolicies.SOURCE_PRIORITY_V1,
                ScoringPolicies.INTEREST_MATCH_V1,
                ScoringPolicies.FRESHNESS_V1,
                ScoringPolicies.POPULARITY_V1,
                ScoringPolicies.CONTEXT_MATCH_V1,
                ScoringPolicies.SCORE_COMPOSITION_V1
        );
        policies.forEach(policy -> emit("POLICY", policy.policyVersion(), policy.effectiveFrom().toString()));
    }

    private void eventWeights() {
        for (EventType eventType : EventType.values()) {
            Double weight = FoundationPolicies.EVENT_WEIGHT_V1.weights().get(eventType);
            emit("EVENT_WEIGHT", eventType.wireValue(), weight == null ? "null" : bits(weight));
        }
    }

    private void features() {
        FeatureVocabularyV1.getAllFeatures().forEach(feature -> emit(
                "FEATURE",
                feature.id(),
                feature.group().wireValue(),
                feature.key(),
                feature.displayName(),
                feature.status().wireValue()
        ));
    }

    private void foundationContracts() {
        emit("SOURCE_PRIORITY", joinWire(FoundationPolicies.SOURCE_PRIORITY_V1.priority().stream().map(item -> item.wireValue()).toList()));
        var explicit = FoundationPolicies.COLD_START_V1.explicitPreference();
        emit(
                "COLD_EXPLICIT",
                bits(explicit.explicitPreferenceMatch()),
                bits(explicit.freshness()),
                bits(explicit.popularity()),
                bits(explicit.explorationDiversity())
        );
        var empty = FoundationPolicies.COLD_START_V1.emptyProfile();
        emit("COLD_EMPTY", bits(empty.freshness()), bits(empty.popularity()), bits(empty.explorationDiversity()));
        emit(
                "CANDIDATE_LIMIT",
                Integer.toString(CandidateLimitPolicies.V1.maxCandidatesToScore()),
                Integer.toString(CandidateLimitPolicies.V1.defaultResultLimit()),
                Integer.toString(CandidateLimitPolicies.V1.hardResultLimit())
        );

        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < 150; index++) {
            candidates.add(index);
        }
        emit("LIMIT", joinIntegers(CandidateLimiter.limitCandidates(candidates, CandidateLimitPolicies.V1)));
        emit("LIMIT_7", joinIntegers(CandidateLimiter.limitCandidates(candidates.subList(0, 10), CandidateLimitPolicies.V1, 7)));

        for (int occurrence : new int[]{1, 2, 3, 100}) {
            var result = RepeatDecay.apply(1.0, occurrence, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1);
            emit("REPEAT", Integer.toString(occurrence), bits(result.multiplier()), bits(result.decayedDelta()));
        }

        Instant referenceTime = Instant.parse("2026-07-01T00:00:00Z");
        for (String occurredAt : List.of(
                "2026-06-30T00:00:00Z",
                "2026-06-24T00:00:00Z",
                "2026-06-21T00:00:00Z",
                "2026-06-01T00:00:00Z",
                "2026-05-02T00:00:00Z",
                "2026-04-02T00:00:00Z",
                "2026-03-03T00:00:00Z"
        )) {
            var result = TimeDecay.apply(Instant.parse(occurredAt), referenceTime, FoundationPolicies.TIME_DECAY_V1);
            emit(
                    "TIME",
                    occurredAt,
                    bits(result.multiplier()),
                    Long.toString(result.elapsedMilliseconds()),
                    bits(result.elapsedDays()),
                    result.bucketId()
            );
        }
        for (double raw : new double[]{-5, 0, 1, 5, 10, 95, 100}) {
            emit("SATURATION", integerLabel(raw), bits(ScoreSaturation.saturate(raw, FoundationPolicies.SATURATION_V1)));
        }
    }

    private void scoringContracts() {
        var interest = ScoringPolicies.INTEREST_MATCH_V1;
        emit(
                "INTEREST_POLICY",
                bits(interest.hardAvoidContributionThreshold()),
                joinWire(interest.hardAvoidSources().stream().map(item -> item.wireValue()).toList()),
                Boolean.toString(interest.exactFeatureMatchOnly()),
                bits(interest.scoreMinimum()),
                bits(interest.scoreMaximum())
        );

        var freshness = ScoringPolicies.FRESHNESS_V1;
        emit("FRESHNESS_ELIGIBLE", joinEntityTypes(freshness.eligibleEntityTypes()));
        for (RecommendationEntityType entityType : freshness.eligibleEntityTypes()) {
            emit("FRESHNESS_HALF_LIFE", entityType.wireValue(), bits(freshness.halfLifeDaysByEntityType().get(entityType)));
        }
        emit("FRESHNESS_ALLOWED", joinWire(freshness.allowedTimestampSources().stream().map(item -> item.wireValue()).toList()));
        emit(
                "FRESHNESS_RANGE",
                bits(freshness.scoreMinimum()),
                bits(freshness.scoreMaximum()),
                Long.toString(freshness.millisecondsPerDay())
        );

        var popularity = ScoringPolicies.POPULARITY_V1;
        emit("POPULARITY_ELIGIBLE", joinEntityTypes(popularity.eligibleEntityTypes()));
        for (RecommendationEntityType entityType : popularity.eligibleEntityTypes()) {
            emit(
                    "POPULARITY_ENTITY",
                    entityType.wireValue(),
                    Integer.toString(popularity.windowDaysByEntityType().get(entityType)),
                    Integer.toString(popularity.referenceExposureByEntityType().get(entityType))
            );
        }
        emit(
                "POPULARITY_CORE",
                Integer.toString(popularity.minimumUniqueExposure()),
                bits(popularity.zScore()),
                bits(popularity.signalWeights().like()),
                bits(popularity.signalWeights().save()),
                bits(popularity.signalWeights().share()),
                bits(popularity.baseEvidenceMultiplier()),
                bits(popularity.volumeEvidenceWeight()),
                bits(popularity.scoreMinimum()),
                bits(popularity.scoreMaximum()),
                Long.toString(popularity.millisecondsPerDay())
        );

        var context = ScoringPolicies.CONTEXT_MATCH_V1;
        emit("CONTEXT_SCHEMA", context.schemaVersion().wireValue());
        emit("CONTEXT_ELIGIBLE", joinEntityTypes(context.eligibleEntityTypes()));
        emit("CONTEXT_HARD_REQUIRED", joinWire(context.hardRequiredGroups().stream().map(item -> item.wireValue()).toList()));
        emit("CONTEXT_HARD_EXCLUDED", joinWire(context.hardExcludedGroups().stream().map(item -> item.wireValue()).toList()));
        emit("CONTEXT_HARD_CLAUSE_SOURCES", joinWire(context.hardClauseSources().stream().map(item -> item.wireValue()).toList()));
        emit("CONTEXT_HARD_FEATURE_SOURCES", joinWire(context.hardEntityFeatureSources().stream().map(item -> item.wireValue()).toList()));
        emit("CONTEXT_SOFT_ALLOWED", joinWire(context.softAllowedGroups().stream().map(item -> item.wireValue()).toList()));
        emit("CONTEXT_SOFT_CLAUSE_SOURCES", joinWire(context.softClauseSources().stream().map(item -> item.wireValue()).toList()));
        emit("CONTEXT_SOFT_FEATURE_SOURCES", joinWire(context.softEntityFeatureSources().stream().map(item -> item.wireValue()).toList()));
        emit(
                "CONTEXT_CORE",
                bits(context.hardMinimumEntityFeatureWeight()),
                Boolean.toString(context.exactFeatureMatchOnly()),
                Long.toString(context.maxSessionLifetimeMilliseconds()),
                Long.toString(context.millisecondsPerDay()),
                bits(context.scoreMinimum()),
                bits(context.scoreMaximum())
        );

        var score = ScoringPolicies.SCORE_COMPOSITION_V1;
        emit("SCORE_ELIGIBLE", joinEntityTypes(score.eligibleEntityTypes()));
        emit("SCORE_COMPONENT_ORDER", joinScoreComponents(score.componentOrder()));
        for (ScoreComponentName component : score.componentOrder()) {
            emit("SCORE_WEIGHT", component.wireValue(), bits(score.globalBaseWeights().get(component)));
        }
        for (RecommendationEntityType entityType : score.eligibleEntityTypes()) {
            emit(
                    "SCORE_ENTITY_COMPONENTS",
                    entityType.wireValue(),
                    joinScoreComponents(score.entityComponentEligibility().get(entityType))
            );
        }
        emit("SCORE_ANCHORS", joinScoreComponents(score.anchorComponents()));
        emit(
                "SCORE_EXPECTED_POLICIES",
                score.expectedComponentPolicyVersions().contextMatch(),
                score.expectedComponentPolicyVersions().interestMatch(),
                score.expectedComponentPolicyVersions().freshness(),
                score.expectedComponentPolicyVersions().popularity()
        );
        emit("SCORE_RANGE", bits(score.neutralPrior()), bits(score.scoreMinimum()), bits(score.scoreMaximum()));
    }

    private void explicitSignals() {
        var signals = ExplicitInterestSignalBuilder.build(List.of(
                new ExplicitPreference("user-b", "theme:cafe", PreferenceKind.PREFER, 0.5, Instant.parse("2026-06-30T00:00:00Z")),
                new ExplicitPreference("user-a", "region:seoul", PreferenceKind.AVOID, 1.0, Instant.parse("2026-06-29T00:00:00Z"))
        ));
        signals.forEach(signal -> emit(
                "EXPLICIT_SIGNAL",
                signal.signalId(),
                signal.userId(),
                signal.featureId(),
                signal.direction().wireValue(),
                bits(signal.strength()),
                signal.source().wireValue(),
                signal.validationStatus().wireValue(),
                signal.updatedAt().toString()
        ));
    }

    private void stateEvents() {
        var tie = StateEventResolver.resolve(List.of(
                event("b", EventType.UNLIKE, "2026-06-30T00:00:00Z", "b", UserBehaviorEventMetadata.empty()),
                event("a", EventType.LIKE, "2026-06-30T00:00:00Z", "a", UserBehaviorEventMetadata.empty())
        ));
        emit("STATE_TIE_EFFECTIVE", joinWire(tie.effectiveEvents().stream().map(UserBehaviorEvent::eventId).toList()));
        tie.finalStates().forEach(state -> emit("STATE_TIE_FINAL", state.key(), Boolean.toString(state.active())));

        UserBehaviorEvent first = event("a", EventType.SAVE, "2026-06-30T00:00:00Z", "same", UserBehaviorEventMetadata.empty());
        UserBehaviorEvent retry = event("b", EventType.SAVE, "2026-06-30T00:00:00Z", "same", UserBehaviorEventMetadata.empty());
        var duplicate = StateEventResolver.resolve(List.of(first, retry));
        emit("STATE_DUP_EFFECTIVE", joinWire(duplicate.effectiveEvents().stream().map(UserBehaviorEvent::eventId).toList()));
        emit(
                "STATE_DUP_IGNORED",
                joinWire(duplicate.ignoredEvents().stream()
                        .map(item -> item.event().eventId() + ":" + item.reason().wireValue())
                        .toList())
        );

        UserBehaviorEvent payload = event(
                "payload",
                EventType.LIKE,
                "2026-06-30T00:00:00.123Z",
                "payload-key",
                new UserBehaviorEventMetadata(EventSurface.HOME, 3, "카페", 1500L, 0.5)
        );
        emit("STATE_CANONICAL_PAYLOAD", CanonicalJson.stringify(payload.payloadWithoutEventId()));
    }

    private static UserBehaviorEvent event(
            String eventId,
            EventType eventType,
            String occurredAt,
            String idempotencyKey,
            UserBehaviorEventMetadata metadata
    ) {
        return new UserBehaviorEvent(
                eventId,
                idempotencyKey,
                "user",
                "session",
                eventType,
                "entity",
                null,
                metadata,
                occurredAt
        );
    }

    private void emit(String kind, String... fields) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("fields", List.of(fields));
        records.add(record);
    }

    private static String joinIntegers(List<Integer> values) {
        return values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String joinEntityTypes(List<RecommendationEntityType> values) {
        return joinWire(values.stream().map(RecommendationEntityType::wireValue).toList());
    }

    private static String joinScoreComponents(List<ScoreComponentName> values) {
        return joinWire(values.stream().map(ScoreComponentName::wireValue).toList());
    }

    private static String joinWire(List<String> values) {
        return String.join(",", values);
    }

    private static String bits(double value) {
        return String.format(Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String integerLabel(double value) {
        return Long.toString((long) value);
    }
}
