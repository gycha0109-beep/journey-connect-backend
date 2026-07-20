package com.jc.recommendation.foundation;

import com.jc.recommendation.exposure.RecommendationExposureEventBuilder;
import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.exposure.RecommendationTraceContracts;
import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.exposure.BuildRecommendationExposureEventInput;
import com.jc.recommendation.model.exposure.RecommendationExposureDuplicateReason;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.policy.RecommendationTracePolicies;

import java.util.List;

public final class CoreWave5ExposureContractTest {
    private static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1"
    );

    public static void main(String[] args) {
        verifiesPolicyAndSignedZeroProjection();
        verifiesDeliveryIndependenceAndResolver();
        verifiesStrictValidation();
        System.out.println("Java recommendation core Wave 5 exposure contract: PASS");
    }

    private static void verifiesPolicyAndSignedZeroProjection() {
        RecommendationTraceContracts.validatePolicy(RecommendationTracePolicies.V1);
        RecommendationExposureEventV1 event = event("event-1", "idem-1", "run-1", "session-1",
                "2026-07-18T01:02:03.123Z");
        check(event.candidates().size() == 1, "candidate projection");
        check(event.candidates().getFirst().scoreIsNegativeZero(), "negative zero flag");
        check(event.replayKey().matches("[0-9a-f]{64}"), "replay hash");
        check(event.pageFingerprint().matches("[0-9a-f]{64}"), "page hash");
    }

    private static void verifiesDeliveryIndependenceAndResolver() {
        RecommendationExposureEventV1 first = event("event-1", "idem-1", "run-1", "session-1",
                "2026-07-18T01:02:03.123Z");
        RecommendationExposureEventV1 deliveryChanged = event("event-2", "idem-2", "run-2", "session-2",
                "2026-07-18T01:02:04Z");
        check(first.replayKey().equals(deliveryChanged.replayKey()), "delivery-independent replay key");
        check(first.pageFingerprint().equals(deliveryChanged.pageFingerprint()),
                "delivery-independent page fingerprint");

        RecommendationExposureEventV1 sameKey = event("event-2", "idem-1", "run-1", "session-1",
                "2026-07-18T01:02:03.123Z");
        var resolved = new RecommendationExposureEventResolver().resolve(List.of(sameKey, first));
        check(resolved.resolvedCount() == 1 && resolved.duplicateCount() == 1, "idempotency dedupe");
        check(resolved.duplicateAudits().getFirst().reason()
                == RecommendationExposureDuplicateReason.DUPLICATE_IDEMPOTENCY_KEY, "dedupe reason");
    }

    private static void verifiesStrictValidation() {
        expectFailure(() -> event("event-1", "idem-1", "run-1", "session-1",
                "2026-02-30T00:00:00Z"), "servedAt");
        RecommendationExposureEventV1 first = event("event-1", "idem-1", "run-1", "session-1",
                "2026-07-18T01:02:03.123Z");
        RecommendationExposureEventV1 conflict = event("event-2", "idem-1", "run-1", "session-1",
                "2026-07-18T01:02:04Z");
        expectFailure(() -> new RecommendationExposureEventResolver().resolve(List.of(first, conflict)),
                "IDEMPOTENCY_KEY_CONFLICT");
    }

    private static RecommendationExposureEventV1 event(
            String eventId,
            String key,
            String runId,
            String sessionId,
            String servedAt
    ) {
        CandidateScoreResult scored = new CandidateScoreResult(
                "wave5-contract-user", "wave5-contract-context", "negative-zero", RecommendationEntityType.POST,
                CandidateScoreStatus.SCORED, -0.0d, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL,
                1.0d, 0.0d, List.of(ScoreComponentName.CONTEXT_MATCH), List.of(), null, null,
                "score-composition-v1", VERSIONS, List.of()
        );
        var ranking = new ExplorationEnabledRanker().rank(new RankCandidatesWithExplorationInput(
                "rank-contract", "meta-contract", "explore-contract", "wave5-contract-user",
                "wave5-contract-context", "score-composition-v1", VERSIONS, "contract-seed",
                List.of(scored), List.of(new DiversityCandidateMetadata(
                        "negative-zero", RecommendationEntityType.POST, "author-1", "region:seoul",
                        "theme:cafe", "dup:negative-zero")), List.of(), 1, null, null, null, null
        ));
        return new RecommendationExposureEventBuilder().build(new BuildRecommendationExposureEventInput(
                eventId, key, runId, sessionId, EventSurface.HOME, servedAt, ranking
        ));
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectFailure(Runnable operation, String expectedMessage) {
        try {
            operation.run();
            throw new AssertionError("Expected failure: " + expectedMessage);
        } catch (IllegalArgumentException exception) {
            if (!exception.getMessage().contains(expectedMessage)) {
                throw new AssertionError("Unexpected error: " + exception.getMessage(), exception);
            }
        }
    }
}
