package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.canonical.JsonWire;
import com.jc.recommendation.evaluation.RecommendationBehaviorEventResolver;
import com.jc.recommendation.evaluation.RecommendationOutcomeAttributor;
import com.jc.recommendation.exposure.RecommendationExposureEventBuilder;
import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesInput;
import com.jc.recommendation.model.evaluation.RecommendationAttributionAuditCategory;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exposure.BuildRecommendationExposureEventInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentAvailability;
import com.jc.recommendation.model.score.ScoreComponentBreakdown;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.policy.OfflineEvaluationPolicies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreWave6AttributionGoldenOracle {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );
    private static final List<RecommendationAttributionAuditCategory> AUDIT_ORDER = List.of(
            RecommendationAttributionAuditCategory.MISSING_RECOMMENDATION_RUN,
            RecommendationAttributionAuditCategory.ANONYMOUS_USER,
            RecommendationAttributionAuditCategory.RUN_USER_SESSION_MISMATCH,
            RecommendationAttributionAuditCategory.UNSUPPORTED_EVENT_TYPE,
            RecommendationAttributionAuditCategory.MISSING_ENTITY_ID,
            RecommendationAttributionAuditCategory.UNMATCHED_ENTITY,
            RecommendationAttributionAuditCategory.AMBIGUOUS_ENTITY_IDENTITY,
            RecommendationAttributionAuditCategory.AFTER_CUTOFF,
            RecommendationAttributionAuditCategory.BEFORE_EXPOSURE,
            RecommendationAttributionAuditCategory.OUTSIDE_ATTRIBUTION_WINDOW,
            RecommendationAttributionAuditCategory.ATTRIBUTED
    );
    private final List<Map<String, Object>> records = new ArrayList<>();

    public static void main(String[] args) {
        new CoreWave6AttributionGoldenOracle().run();
    }

    private void run() {
        RankCandidatesWithExplorationResult ranking = rankingResult();
        RecommendationExposureEventBuilder exposureBuilder = new RecommendationExposureEventBuilder();
        var exposureOne = exposureBuilder.build(new BuildRecommendationExposureEventInput(
                "exp-1", "exp-key-1", "run-wave6", "session-wave6", EventSurface.HOME,
                "2026-07-18T01:02:00Z", ranking));
        var exposureTwo = exposureBuilder.build(new BuildRecommendationExposureEventInput(
                "exp-2", "exp-key-2", "run-wave6", "session-wave6", EventSurface.HOME,
                "2026-07-18T01:05:00Z", ranking));
        var exposureResult = new RecommendationExposureEventResolver().resolve(List.of(exposureTwo, exposureOne));

        List<UserBehaviorEvent> behaviorInput = new ArrayList<>();
        behaviorInput.add(behavior("b-click", EventType.CLICK, "2026-07-18T01:03:00Z", "p1",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-like", EventType.LIKE, "2026-07-18T01:06:00Z", "p1",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-report", EventType.REPORT, "2026-07-18T01:07:00Z", "e1",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-hide", EventType.HIDE, "2026-07-18T01:08:00Z", "p2",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-missing-run", EventType.CLICK, "2026-07-18T01:09:00Z", "p1",
                "wave6-user", "session-wave6", null));
        behaviorInput.add(behavior("b-anonymous", EventType.CLICK, "2026-07-18T01:10:00Z", "p1",
                null, "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-mismatch", EventType.CLICK, "2026-07-18T01:11:00Z", "p1",
                "wave6-user", "other", "run-wave6"));
        behaviorInput.add(behavior("b-unsupported", EventType.IMPRESSION, "2026-07-18T01:12:00Z", "p1",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-missing-entity", EventType.CLICK, "2026-07-18T01:13:00Z", null,
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-unmatched", EventType.CLICK, "2026-07-18T01:14:00Z", "unknown",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-before", EventType.CLICK, "2026-07-18T01:00:00Z", "p2",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-outside", EventType.CLICK, "2026-07-18T01:40:00Z", "p3",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behavior("b-after-cutoff", EventType.CLICK, "2026-07-18T02:01:00Z", "p4",
                "wave6-user", "session-wave6", "run-wave6"));
        behaviorInput.add(behaviorInput.getFirst());
        var behaviorResult = new RecommendationBehaviorEventResolver().resolve(behaviorInput);
        var result = new RecommendationOutcomeAttributor().attribute(new AttributeRecommendationOutcomesInput(
                "case-wave6", exposureResult, behaviorResult, "2026-07-18T02:00:00Z"));

        var policy = OfflineEvaluationPolicies.V1;
        emit("POLICY", policy.policyVersion(), policy.expectedExposureSchemaVersion(),
                policy.expectedTracePolicyVersion(), policy.expectedRankingPolicyVersion(),
                policy.expectedCursorVersion(), policy.expectedOutcomeValuePolicyVersion(),
                policy.attributionWindowMsByEventType().get(EventType.CLICK),
                policy.attributionWindowMsByEventType().get(EventType.LIKE));
        emit("BEHAVIOR_RESOLVE", behaviorResult.inputCount(), behaviorResult.resolvedCount(),
                behaviorResult.duplicateCount(),
                String.join(",", behaviorResult.resolvedEvents().stream().map(value -> value.eventId()).toList()),
                String.join(",", behaviorResult.duplicateAudits().stream()
                        .map(value -> value.duplicateEventId() + ":" + value.reason().wireValue()).toList()));
        emit("RESULT", result.caseId(), result.recommendationRunId(), result.replayKey(),
                result.resolvedBehaviorEventCount(), result.attributedOutcomeEventCount(),
                result.attributedNumericEventCount(), bits(result.associatedOutcomeValue()), result.clickCount(),
                result.positiveEventCount(), result.negativeEventCount(), result.severeReportCount(),
                result.ambiguousOutcomeEventCount(), result.unmatchedOutcomeEventCount(),
                result.runUserSessionMismatchCount());
        List<Object> countFields = new ArrayList<>();
        for (RecommendationAttributionAuditCategory category : AUDIT_ORDER) {
            countFields.add(category.wireValue());
            countFields.add(result.auditCounts().get(category));
        }
        emit("AUDIT_COUNTS", countFields.toArray());
        for (var attribution : result.attributions()) {
            emit("ATTRIBUTION", attribution.behaviorEventId(), attribution.behaviorEventType().wireValue(),
                    attribution.recommendationRunId(), attribution.exposureEventId(),
                    attribution.entityType().wireValue(), attribution.entityId(), attribution.exposureServedAt(),
                    attribution.behaviorOccurredAt(), attribution.elapsedMs(), attribution.attributionWindowMs(),
                    nullable(attribution.associatedOutcomeValue()), attribution.isPositive(), attribution.isNegative(),
                    attribution.isSevereReport());
        }
        for (var audit : result.audits()) {
            emit("AUDIT", audit.behaviorEventId(), audit.category().wireValue(),
                    nullable(audit.recommendationRunId()), nullable(audit.entityId()),
                    audit.resolvedEntityType() == null ? "null" : audit.resolvedEntityType().wireValue(),
                    nullable(audit.exposureEventId()));
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "wave6-attribution-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 6 golden", exception);
        }
    }

    private static UserBehaviorEvent behavior(
            String eventId, EventType eventType, String occurredAt, String entityId,
            String userId, String sessionId, String runId
    ) {
        return new UserBehaviorEvent(eventId, "key-" + eventId, userId, sessionId, eventType,
                entityId, runId, UserBehaviorEventMetadata.empty(), occurredAt);
    }

    private static RankCandidatesWithExplorationResult rankingResult() {
        List<CandidateScoreResult> candidates = new ArrayList<>();
        List<DiversityCandidateMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            String id = "p" + (index + 1);
            candidates.add(scored(id, 1.0 - index * 0.05, RecommendationEntityType.POST));
            metadata.add(metadata(id, RecommendationEntityType.POST, index));
        }
        candidates.add(terminal("e1", RecommendationEntityType.POST, 0.9, 0.8));
        candidates.add(terminal("e2", RecommendationEntityType.JOURNEY, 0.85, 0.9));
        List<ExplorationCandidateMetadata> explorationMetadata = List.of(
                explorationMetadata("e1", RecommendationEntityType.POST, 0),
                explorationMetadata("e2", RecommendationEntityType.JOURNEY, 1)
        );
        return new ExplorationEnabledRanker().rank(new RankCandidatesWithExplorationInput(
                "rank-wave6", "meta-wave6", "explore-wave6", "wave6-user", "wave6-context",
                "score-composition-v1", VERSIONS, "서울🌏wave6", candidates, metadata,
                explorationMetadata, 6, null, null, null, null));
    }

    private static CandidateScoreResult scored(String id, double score, RecommendationEntityType type) {
        return new CandidateScoreResult("wave6-user", "wave6-context", id, type, CandidateScoreStatus.SCORED,
                score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL, 1.0, 0.0,
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH), List.of(),
                null, null, "score-composition-v1", VERSIONS, List.of());
    }

    private static CandidateScoreResult terminal(String id, RecommendationEntityType type, double fresh, double pop) {
        return new CandidateScoreResult("wave6-user", "wave6-context", id, type,
                CandidateScoreStatus.NOT_APPLICABLE, null, null, null, null, List.of(), List.of(),
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, null, "score-composition-v1", VERSIONS,
                List.of(row(ScoreComponentName.CONTEXT_MATCH, null), row(ScoreComponentName.INTEREST_MATCH, null),
                        row(ScoreComponentName.FRESHNESS, fresh), row(ScoreComponentName.POPULARITY, pop)));
    }

    private static ScoreComponentBreakdown row(ScoreComponentName component, Double rawScore) {
        String version = switch (component) {
            case CONTEXT_MATCH -> VERSIONS.contextMatch();
            case INTEREST_MATCH -> VERSIONS.interestMatch();
            case FRESHNESS -> VERSIONS.freshness();
            case POPULARITY -> VERSIONS.popularity();
        };
        if (rawScore == null) return new ScoreComponentBreakdown(component, "not_applicable", "not_available",
                version, 1.0, 1.0, ScoreComponentAvailability.NEUTRAL_FILLED, null, 0.5, null);
        return new ScoreComponentBreakdown(component, "scored", null, version, 1.0, 1.0,
                ScoreComponentAvailability.SCORED, rawScore, rawScore, rawScore * 0.1);
    }

    private static DiversityCandidateMetadata metadata(String id, RecommendationEntityType type, int index) {
        return new DiversityCandidateMetadata(id, type, "author-" + (index % 3),
                index % 2 == 0 ? "region:seoul" : "region:busan",
                index % 2 == 0 ? "theme:cafe" : "theme:nature", "dup:" + id);
    }

    private static ExplorationCandidateMetadata explorationMetadata(String id, RecommendationEntityType type, int i) {
        DiversityCandidateMetadata base = metadata(id, type, i);
        return new ExplorationCandidateMetadata(base.entityId(), base.entityType(), base.authorId(),
                base.primaryRegionFeatureId(), base.primaryThemeFeatureId(), base.duplicateGroupId(), i);
    }

    private void emit(String kind, Object... values) {
        List<String> fields = new ArrayList<>();
        for (Object value : values) fields.add(String.valueOf(value));
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("fields", fields);
        records.add(record);
    }

    private static String bits(double value) {
        return String.format(java.util.Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String nullable(Object value) {
        if (value == null) return "null";
        if (value instanceof Double || value instanceof Float) return JsonWire.stringify(value);
        return String.valueOf(value);
    }
}
