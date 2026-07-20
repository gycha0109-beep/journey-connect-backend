package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.model.context.ContextClauseSource;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.freshness.FreshnessTimestampSource;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.policy.ScoringPolicies;
import com.jc.recommendation.state.IgnoredStateEventReason;
import com.jc.recommendation.state.StateEventResolver;
import com.jc.recommendation.support.StrictUtc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreWave1ContractTest {
    private CoreWave1ContractTest() {
    }

    public static void main(String[] args) {
        scoringPoliciesMatchReferenceContracts();
        stateEventsMatchReferenceContracts();
        canonicalPayloadMatchesReferenceContract();
        strictUtcMatchesCoreBoundary();
        System.out.println("Java recommendation core Wave 1 contract: PASS");
    }

    private static void scoringPoliciesMatchReferenceContracts() {
        equal("interest-match-v1", ScoringPolicies.INTEREST_MATCH_V1.policyVersion(), "interest version");
        rawEqual(0.8, ScoringPolicies.INTEREST_MATCH_V1.hardAvoidContributionThreshold(), "hard avoid threshold");
        equal(List.of(FeatureSource.EXPLICIT), ScoringPolicies.INTEREST_MATCH_V1.hardAvoidSources(), "hard avoid sources");

        equal(
                List.of(RecommendationEntityType.POST, RecommendationEntityType.JOURNEY),
                ScoringPolicies.FRESHNESS_V1.eligibleEntityTypes(),
                "freshness eligible"
        );
        rawEqual(14.0, ScoringPolicies.FRESHNESS_V1.halfLifeDaysByEntityType().get(RecommendationEntityType.POST), "post half life");
        rawEqual(30.0, ScoringPolicies.FRESHNESS_V1.halfLifeDaysByEntityType().get(RecommendationEntityType.JOURNEY), "journey half life");
        equal(
                List.of(
                        FreshnessTimestampSource.PUBLISHED_AT,
                        FreshnessTimestampSource.CREATED_AT,
                        FreshnessTimestampSource.MEANINGFUL_UPDATED_AT
                ),
                ScoringPolicies.FRESHNESS_V1.allowedTimestampSources(),
                "freshness timestamp sources"
        );

        equal(20, ScoringPolicies.POPULARITY_V1.minimumUniqueExposure(), "minimum exposure");
        rawEqual(1.96, ScoringPolicies.POPULARITY_V1.zScore(), "z score");
        rawEqual(0.5, ScoringPolicies.POPULARITY_V1.signalWeights().save(), "save weight");

        equal("context-v1", ScoringPolicies.CONTEXT_MATCH_V1.schemaVersion().wireValue(), "context schema");
        equal(
                List.of(FeatureGroup.REGION, FeatureGroup.ENVIRONMENT, FeatureGroup.TRANSPORT),
                ScoringPolicies.CONTEXT_MATCH_V1.hardRequiredGroups(),
                "hard required groups"
        );
        equal(
                List.of(ContextClauseSource.EXPLICIT, ContextClauseSource.VALIDATED_QUERY),
                ScoringPolicies.CONTEXT_MATCH_V1.hardClauseSources(),
                "hard clause sources"
        );

        equal(
                List.of(
                        ScoreComponentName.CONTEXT_MATCH,
                        ScoreComponentName.INTEREST_MATCH,
                        ScoreComponentName.FRESHNESS,
                        ScoreComponentName.POPULARITY
                ),
                ScoringPolicies.SCORE_COMPOSITION_V1.componentOrder(),
                "component order"
        );
        rawEqual(0.40, ScoringPolicies.SCORE_COMPOSITION_V1.globalBaseWeights().get(ScoreComponentName.CONTEXT_MATCH), "context weight");
        rawEqual(0.35, ScoringPolicies.SCORE_COMPOSITION_V1.globalBaseWeights().get(ScoreComponentName.INTEREST_MATCH), "interest weight");
        equal(
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH),
                ScoringPolicies.SCORE_COMPOSITION_V1.entityComponentEligibility().get(RecommendationEntityType.PLACE),
                "place components"
        );
        expectUnsupported(() -> ScoringPolicies.SCORE_COMPOSITION_V1.eligibleEntityTypes().add(RecommendationEntityType.USER));
        expectUnsupported(() -> ScoringPolicies.FRESHNESS_V1.halfLifeDaysByEntityType().put(RecommendationEntityType.PLACE, 1.0));
    }

    private static void stateEventsMatchReferenceContracts() {
        var cancellation = StateEventResolver.resolve(List.of(
                event("a", EventType.LIKE, "2026-06-30T00:00:00Z"),
                event("b", EventType.UNLIKE, "2026-06-30T00:01:00Z")
        ));
        equal(List.of("a", "b"), cancellation.effectiveEvents().stream().map(UserBehaviorEvent::eventId).toList(), "cancellation events");
        equal(false, cancellation.finalStates().getFirst().active(), "cancellation final state");

        var repeated = StateEventResolver.resolve(List.of(
                event("a", EventType.LIKE, "2026-06-30T00:00:00Z"),
                event("b", EventType.LIKE, "2026-06-30T00:01:00Z"),
                event("c", EventType.UNLIKE, "2026-06-30T00:02:00Z"),
                event("d", EventType.UNLIKE, "2026-06-30T00:03:00Z")
        ));
        equal(
                List.of(IgnoredStateEventReason.DUPLICATE_STATE, IgnoredStateEventReason.INVALID_INVERSE_STATE),
                repeated.ignoredEvents().stream().map(item -> item.reason()).toList(),
                "state ignored reasons"
        );

        UserBehaviorEvent first = event("a", EventType.SAVE, "2026-06-30T00:00:00Z", "same");
        UserBehaviorEvent retry = new UserBehaviorEvent(
                "b",
                first.idempotencyKey(),
                first.userId(),
                first.sessionId(),
                first.eventType(),
                first.entityId(),
                first.recommendationRunId(),
                first.metadata(),
                first.occurredAt()
        );
        var duplicate = StateEventResolver.resolve(List.of(first, retry));
        equal(List.of("a"), duplicate.effectiveEvents().stream().map(UserBehaviorEvent::eventId).toList(), "deduplicated effective events");
        equal(IgnoredStateEventReason.DUPLICATE_IDEMPOTENCY, duplicate.ignoredEvents().getFirst().reason(), "idempotency reason");

        expectIllegalArgumentContains(
                () -> StateEventResolver.resolve(List.of(
                        event("a", EventType.SAVE, "2026-06-30T00:00:00Z", "same"),
                        event("b", EventType.LIKE, "2026-06-30T00:00:00Z", "same")
                )),
                "Conflicting payload"
        );

        List<UserBehaviorEvent> input = List.of(
                event("b", EventType.UNLIKE, "2026-06-30T00:00:00Z"),
                event("a", EventType.LIKE, "2026-06-30T00:00:00Z")
        );
        var tie = StateEventResolver.resolve(input);
        equal(List.of("a", "b"), tie.effectiveEvents().stream().map(UserBehaviorEvent::eventId).toList(), "eventId tie-break");
        equal(List.of("b", "a"), input.stream().map(UserBehaviorEvent::eventId).toList(), "input order unchanged");

        expectIllegalArgumentContains(
                () -> StateEventResolver.resolve(List.of(event("x", EventType.VIEW, "2026-06-30T00:00:00Z"))),
                "Unsupported state event type"
        );
        expectIllegalArgumentContains(
                () -> StateEventResolver.resolve(List.of(event("x", EventType.LIKE, "not-a-date"))),
                "invalid occurredAt"
        );
    }

    private static void canonicalPayloadMatchesReferenceContract() {
        UserBehaviorEvent event = new UserBehaviorEvent(
                "payload",
                "payload-key",
                "user",
                "session",
                EventType.LIKE,
                "entity",
                null,
                new UserBehaviorEventMetadata(EventSurface.HOME, 3, "카페", 1500L, 0.5),
                "2026-06-30T00:00:00.123Z"
        );
        equal(
                "{\"entityId\":\"entity\",\"eventType\":\"like\",\"idempotencyKey\":\"payload-key\",\"metadata\":{\"dwellTimeMs\":1500,\"position\":3,\"query\":\"카페\",\"surface\":\"home\",\"viewportRatio\":0.5},\"occurredAt\":\"2026-06-30T00:00:00.123Z\",\"sessionId\":\"session\",\"userId\":\"user\"}",
                CanonicalJson.stringify(event.payloadWithoutEventId()),
                "canonical state payload"
        );

        Map<String, Object> signedZero = new LinkedHashMap<>();
        signedZero.put("value", -0.0d);
        equal("{\"value\":0}", CanonicalJson.stringify(signedZero), "JSON signed zero semantics");
        expectIllegalArgumentContains(() -> CanonicalJson.stringify(Map.of("value", Double.NaN)), "finite");
    }

    private static void strictUtcMatchesCoreBoundary() {
        equal(
                Instant.parse("2026-06-30T00:00:00.123Z"),
                StrictUtc.parse("2026-06-30T00:00:00.123Z", "timestamp"),
                "strict UTC parse"
        );
        equal(1782777600123L, StrictUtc.parseEpochMilli("2026-06-30T00:00:00.123Z", "timestamp"), "epoch millis");
        expectIllegalArgumentContains(() -> StrictUtc.parse("2026-06-30T00:00:00+09:00", "timestamp"), "ending in Z");
        expectIllegalArgumentContains(() -> StrictUtc.parse("not-a-dateZ", "timestamp"), "valid ISO 8601 UTC");
    }

    private static UserBehaviorEvent event(String eventId, EventType eventType, String occurredAt) {
        return event(eventId, eventType, occurredAt, eventId);
    }

    private static UserBehaviorEvent event(String eventId, EventType eventType, String occurredAt, String idempotencyKey) {
        return new UserBehaviorEvent(
                eventId,
                idempotencyKey,
                "user",
                "session",
                eventType,
                "entity",
                null,
                UserBehaviorEventMetadata.empty(),
                occurredAt
        );
    }

    private static void expectIllegalArgumentContains(Runnable runnable, String expectedMessagePart) {
        try {
            runnable.run();
            throw new AssertionError("Expected IllegalArgumentException containing: " + expectedMessagePart);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() == null || !exception.getMessage().contains(expectedMessagePart)) {
                throw new AssertionError("Exception message did not contain: " + expectedMessagePart, exception);
            }
        }
    }

    private static void expectUnsupported(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void rawEqual(double expected, double actual, String label) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
