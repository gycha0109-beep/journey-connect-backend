package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.exposure.RecommendationExposureEventBuilder;
import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exposure.BuildRecommendationExposureEventInput;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
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
import com.jc.recommendation.policy.RecommendationTracePolicies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreWave5ExposureGoldenOracle {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );
    private final RecommendationExposureEventBuilder builder = new RecommendationExposureEventBuilder();
    private final RecommendationExposureEventResolver resolver = new RecommendationExposureEventResolver();
    private final List<Map<String, Object>> records = new ArrayList<>();
    private final RankCandidatesWithExplorationResult rankingResult = rankingResult();

    public static void main(String[] args) {
        new CoreWave5ExposureGoldenOracle().run();
    }

    private void run() {
        var policy = RecommendationTracePolicies.V1;
        emit("POLICY", policy.policyVersion(), policy.effectiveFrom().toString(), policy.eventSchemaVersion(),
                policy.expectedRankingPolicyVersion(), policy.expectedCursorVersion(),
                policy.maximumPageCandidateCount(), policy.fingerprintAlgorithm());
        RecommendationExposureEventV1 event = event("event-1", "idem-1", "run-1", "session-1",
                EventSurface.HOME, "2026-07-18T01:02:03.123Z");
        emit("EVENT", event.schemaVersion(), event.eventId(), event.idempotencyKey(),
                event.recommendationRunId(), event.userId(), event.sessionId(), event.contextId(),
                event.surface().wireValue(), event.servedAt(), event.replayKey(), event.pageFingerprint(),
                event.cursorVersion(), event.rankingStatus().wireValue(), nullable(event.rankingEmptyReason()),
                nullable(event.requestedLimit()), event.effectiveLimit(), nullable(event.pageStartRank()),
                nullable(event.pageEndRank()), event.pageCandidateCount(), event.hasNextPage(), event.inputCount(),
                event.finalRankedCandidateCount(), event.terminalCandidateCount());
        for (var candidate : event.candidates()) {
            emit("CANDIDATE", candidate.pagePosition(), candidate.absoluteRank(), candidate.entityId(),
                    candidate.entityType().wireValue(), candidate.origin().wireValue(),
                    candidate.score() == null ? "null" : bits(candidate.score()), candidate.scoreIsNegativeZero(),
                    nullable(candidate.baseAbsoluteRank()), nullable(candidate.diversifiedAbsoluteRank()),
                    candidate.explorationQualityScore() == null ? "null" : bits(candidate.explorationQualityScore()),
                    nullable(candidate.recentExposureCount()), nullable(candidate.seededTieBreakKey()),
                    nullable(candidate.explorationPoolRank()), nullable(candidate.targetInsertionRank()));
        }
        emit("DIVERSITY", event.diversitySummary().status().wireValue(),
                event.diversitySummary().movedCandidateCount(), event.diversitySummary().maxPromotionObserved(),
                event.diversitySummary().maxDemotionObserved(),
                event.diversitySummary().movementBoundForcedCount());
        emit("EXPLORATION", event.explorationSummary().status().wireValue(),
                event.explorationSummary().structurallyEligibleCandidateCount(),
                event.explorationSummary().eligibleCandidateCount(),
                event.explorationSummary().insertedCandidateCount(), event.explorationSummary().skippedSlotCount(),
                join(event.explorationSummary().insertedTargetRanks()),
                String.join(",", event.explorationSummary().slotDecisions().stream()
                        .map(value -> value.targetInsertionRank() + ":" + value.status().wireValue()).toList()));

        RecommendationExposureEventV1 deliveryChanged = event("event-2", "idem-2", "run-2", "session-2",
                EventSurface.SEARCH, "2026-07-18T01:02:04Z");
        emit("DELIVERY_INDEPENDENCE", event.replayKey().equals(deliveryChanged.replayKey()),
                event.pageFingerprint().equals(deliveryChanged.pageFingerprint()));
        RecommendationExposureEventV1 sameKey = event("event-2", "idem-1", "run-1", "session-1",
                EventSurface.HOME, "2026-07-18T01:02:03.123Z");
        RecommendationExposureEventV1 sameEvent = event("event-1", "idem-2", "run-1", "session-1",
                EventSurface.HOME, "2026-07-18T01:02:03.123Z");
        emitResolve("same-key", List.of(sameKey, event));
        emitResolve("same-event", List.of(sameEvent, event));
        emitResolve("both", List.of(event, event));
        emitResolve("reexposure", List.of(deliveryChanged, event));

        emitError("key", () -> resolver.resolve(List.of(event,
                event("event-2", "idem-1", "run-1", "session-1", EventSurface.HOME,
                        "2026-07-18T01:02:04Z"))));
        emitError("event", () -> resolver.resolve(List.of(event,
                event("event-1", "idem-2", "run-2", "session-1", EventSurface.HOME,
                        "2026-07-18T01:02:03.123Z"))));
        emitError("run", () -> resolver.resolve(List.of(event,
                event("event-2", "idem-2", "run-1", "other", EventSurface.HOME,
                        "2026-07-18T01:02:03.123Z"))));
        RecommendationExposureEventV1 bridgeC = event("event-2", "idem-2", "run-1", "session-1",
                EventSurface.HOME, "2026-07-18T01:02:03.123Z");
        emitError("bridge", () -> resolver.resolve(List.of(event, sameEvent, bridgeC)));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "wave5-exposure-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 5 golden", exception);
        }
    }

    private void emitResolve(String label, List<RecommendationExposureEventV1> events) {
        var result = resolver.resolve(events);
        emit("RESOLVE", label, result.inputCount(), result.resolvedCount(), result.duplicateCount(),
                String.join(",", result.resolvedEvents().stream().map(RecommendationExposureEventV1::eventId).toList()),
                String.join(",", result.duplicateAudits().stream().map(audit -> audit.duplicateEventId() + ":"
                        + audit.duplicateIdempotencyKey() + ":" + audit.keptEventId() + ":"
                        + audit.keptIdempotencyKey() + ":" + audit.reason().wireValue()).toList()));
    }

    private void emitError(String label, Runnable operation) {
        try {
            operation.run();
            emit("ERROR", label, "none");
        } catch (IllegalArgumentException exception) {
            emit("ERROR", label, exception.getMessage().split(":", 2)[0]);
        }
    }

    private RecommendationExposureEventV1 event(
            String eventId,
            String idempotencyKey,
            String runId,
            String sessionId,
            EventSurface surface,
            String servedAt
    ) {
        return builder.build(new BuildRecommendationExposureEventInput(
                eventId, idempotencyKey, runId, sessionId, surface, servedAt, rankingResult
        ));
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
                "rank-wave5", "meta-wave5", "explore-wave5", "wave5-user", "wave5-context",
                "score-composition-v1", VERSIONS, "서울🌏wave5", candidates, metadata,
                explorationMetadata, 6, null, null, null, null
        ));
    }

    private static CandidateScoreResult scored(String id, double score, RecommendationEntityType type) {
        return new CandidateScoreResult("wave5-user", "wave5-context", id, type, CandidateScoreStatus.SCORED,
                score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL, 1.0, 0.0,
                List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH), List.of(),
                null, null, "score-composition-v1", VERSIONS, List.of());
    }

    private static CandidateScoreResult terminal(
            String id,
            RecommendationEntityType type,
            double freshness,
            double popularity
    ) {
        return new CandidateScoreResult("wave5-user", "wave5-context", id, type,
                CandidateScoreStatus.NOT_APPLICABLE, null, null, null, null, List.of(), List.of(),
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, null, "score-composition-v1", VERSIONS,
                List.of(row(ScoreComponentName.CONTEXT_MATCH, null), row(ScoreComponentName.INTEREST_MATCH, null),
                        row(ScoreComponentName.FRESHNESS, freshness), row(ScoreComponentName.POPULARITY, popularity)));
    }

    private static ScoreComponentBreakdown row(ScoreComponentName component, Double rawScore) {
        String version = switch (component) {
            case CONTEXT_MATCH -> VERSIONS.contextMatch();
            case INTEREST_MATCH -> VERSIONS.interestMatch();
            case FRESHNESS -> VERSIONS.freshness();
            case POPULARITY -> VERSIONS.popularity();
        };
        if (rawScore == null) {
            return new ScoreComponentBreakdown(component, "not_applicable", "not_available", version,
                    1.0, 1.0, ScoreComponentAvailability.NEUTRAL_FILLED, null, 0.5, null);
        }
        return new ScoreComponentBreakdown(component, "scored", null, version, 1.0, 1.0,
                ScoreComponentAvailability.SCORED, rawScore, rawScore, rawScore * 0.1);
    }

    private static DiversityCandidateMetadata metadata(String id, RecommendationEntityType type, int index) {
        return new DiversityCandidateMetadata(id, type, "author-" + (index % 3),
                index % 2 == 0 ? "region:seoul" : "region:busan",
                index % 2 == 0 ? "theme:cafe" : "theme:nature", "dup:" + id);
    }

    private static ExplorationCandidateMetadata explorationMetadata(
            String id,
            RecommendationEntityType type,
            int index
    ) {
        DiversityCandidateMetadata base = metadata(id, type, index);
        return new ExplorationCandidateMetadata(base.entityId(), base.entityType(), base.authorId(),
                base.primaryRegionFeatureId(), base.primaryThemeFeatureId(), base.duplicateGroupId(), index);
    }

    private void emit(String kind, Object... values) {
        List<String> fields = new ArrayList<>();
        for (Object value : values) {
            fields.add(String.valueOf(value));
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("fields", fields);
        records.add(record);
    }

    private static String bits(double value) {
        return String.format(java.util.Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String nullable(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof com.jc.recommendation.model.ranking.RankingEmptyReason reason) {
            return reason.wireValue();
        }
        return String.valueOf(value);
    }

    private static String join(List<?> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }
}
