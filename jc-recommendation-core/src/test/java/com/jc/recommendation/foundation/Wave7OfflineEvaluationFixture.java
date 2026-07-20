package com.jc.recommendation.foundation;

import com.jc.recommendation.evaluation.RecommendationBehaviorEventResolver;
import com.jc.recommendation.evaluation.RecommendationOutcomeAttributor;
import com.jc.recommendation.exposure.RecommendationExposureEventBuilder;
import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesInput;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesResult;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exposure.BuildRecommendationExposureEventInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyComparisonResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyVector;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RankingV3ReplayInputSnapshot;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RecommendationOfflineEvaluationCase;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayResult;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentAvailability;
import com.jc.recommendation.model.score.ScoreComponentBreakdown;
import com.jc.recommendation.model.score.ScoreComponentName;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.model.score.ScoreCompositionMode;
import com.jc.recommendation.offline.RankingV3FullResultCollector;
import com.jc.recommendation.offline.RecommendationPolicyComparator;
import com.jc.recommendation.offline.RecommendationReplayEvaluator;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.ExplorationPolicies;
import com.jc.recommendation.policy.RankingIntegrationPolicies;

import java.util.ArrayList;
import java.util.List;

final class Wave7OfflineEvaluationFixture {
    static final ScoreComponentPolicyVersions VERSIONS = new ScoreComponentPolicyVersions(
            "context-match-v1", "interest-match-v1", "freshness-v1", "popularity-v1");

    private Wave7OfflineEvaluationFixture() {
    }

    static Scenario scenario() {
        List<CandidateScoreResult> candidates = new ArrayList<>();
        List<DiversityCandidateMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            String id = "p" + (index + 1);
            candidates.add(scored(id, 1.0d - index * 0.015d, RecommendationEntityType.POST));
            metadata.add(metadata(id, RecommendationEntityType.POST, index));
        }
        candidates.add(terminal("e1", RecommendationEntityType.POST, 0.95d, 0.8d));
        candidates.add(terminal("e2", RecommendationEntityType.JOURNEY, 0.85d, 0.9d));
        List<ExplorationCandidateMetadata> explorationMetadata = List.of(
                explorationMetadata("e1", RecommendationEntityType.POST, 40),
                explorationMetadata("e2", RecommendationEntityType.JOURNEY, 41));
        RankingV3ReplayInputSnapshot snapshot = new RankingV3ReplayInputSnapshot(
                "rank-wave7", "meta-wave7", "explore-wave7", "wave7-user", "wave7-context",
                "score-composition-v1", VERSIONS, "서울🌏stage7", candidates, metadata,
                explorationMetadata, RankingIntegrationPolicies.V3, DiversityPolicies.V1,
                ExplorationPolicies.V1);
        ExplorationEnabledRanker ranker = new ExplorationEnabledRanker();
        RankCandidatesWithExplorationResult page1 = ranker.rank(input(snapshot, 30, null));
        RankCandidatesWithExplorationResult page2 = ranker.rank(input(snapshot, 30, page1.nextCursor()));
        RecommendationExposureEventBuilder builder = new RecommendationExposureEventBuilder();
        var exposure1 = builder.build(new BuildRecommendationExposureEventInput(
                "exp-wave7-1", "idem-wave7-1", "run-wave7", "session-wave7",
                EventSurface.HOME, "2026-07-18T03:00:00Z", page1));
        var exposure2 = builder.build(new BuildRecommendationExposureEventInput(
                "exp-wave7-2", "idem-wave7-2", "run-wave7", "session-wave7",
                EventSurface.HOME, "2026-07-18T03:01:00Z", page2));
        List<UserBehaviorEvent> behaviorEvents = List.of(
                behavior("b7-click", EventType.CLICK, "2026-07-18T03:02:00Z", "p1"),
                behavior("b7-like", EventType.LIKE, "2026-07-18T03:03:00Z", "p31"),
                behavior("b7-report", EventType.REPORT, "2026-07-18T03:04:00Z", "e1"));
        RecommendationOfflineEvaluationCase evaluationCase = new RecommendationOfflineEvaluationCase(
                "case-wave7", snapshot, List.of(exposure2, exposure1), behaviorEvents,
                "2026-07-18T04:00:00Z");
        var collected = new RankingV3FullResultCollector().collect(snapshot);
        ReplayResult replay = new RecommendationReplayEvaluator().evaluate(evaluationCase);
        AttributeRecommendationOutcomesResult attribution = new RecommendationOutcomeAttributor().attribute(
                new AttributeRecommendationOutcomesInput(
                        evaluationCase.caseId(),
                        new RecommendationExposureEventResolver().resolve(evaluationCase.exposureEvents()),
                        new RecommendationBehaviorEventResolver().resolve(evaluationCase.behaviorEvents()),
                        evaluationCase.evaluationCutoffAt()));
        PolicyVector treatment = new PolicyVector(
                RankingIntegrationPolicies.V3, DiversityPolicies.V1, ExplorationPolicies.V1,
                "부산🌊stage7");
        PolicyComparisonResult comparison = new RecommendationPolicyComparator().compare(
                new com.jc.recommendation.model.offline.OfflineEvaluationContracts.CompareInput(
                        evaluationCase, replay, treatment, attribution));
        return new Scenario(evaluationCase, collected, replay, attribution, treatment, comparison);
    }

    private static RankCandidatesWithExplorationInput input(
            RankingV3ReplayInputSnapshot snapshot, int limit, String cursor
    ) {
        return new RankCandidatesWithExplorationInput(
                snapshot.rankingSnapshotId(), snapshot.metadataSnapshotId(), snapshot.explorationSnapshotId(),
                snapshot.userId(), snapshot.contextId(), snapshot.scorePolicyVersion(),
                snapshot.componentPolicyVersions(), snapshot.explorationSeed(), snapshot.candidates(),
                snapshot.candidateMetadata(), snapshot.explorationMetadata(), limit, cursor,
                snapshot.policy(), snapshot.diversityPolicy(), snapshot.explorationPolicy());
    }

    private static UserBehaviorEvent behavior(String id, EventType type, String time, String entityId) {
        return new UserBehaviorEvent(id, "key-" + id, "wave7-user", "session-wave7", type,
                entityId, "run-wave7", UserBehaviorEventMetadata.empty(), time);
    }

    private static CandidateScoreResult scored(String id, double score, RecommendationEntityType type) {
        return new CandidateScoreResult("wave7-user", "wave7-context", id, type,
                CandidateScoreStatus.SCORED, score, ScoreCompositionMode.PERSONALIZED_CONTEXTUAL,
                1.0d, 0.0d, List.of(ScoreComponentName.CONTEXT_MATCH, ScoreComponentName.INTEREST_MATCH),
                List.of(), null, null, "score-composition-v1", VERSIONS, List.of());
    }

    private static CandidateScoreResult terminal(
            String id, RecommendationEntityType type, double freshness, double popularity
    ) {
        return new CandidateScoreResult("wave7-user", "wave7-context", id, type,
                CandidateScoreStatus.NOT_APPLICABLE, null, null, null, null, List.of(), List.of(),
                CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT, null,
                "score-composition-v1", VERSIONS,
                List.of(row(ScoreComponentName.CONTEXT_MATCH, null),
                        row(ScoreComponentName.INTEREST_MATCH, null),
                        row(ScoreComponentName.FRESHNESS, freshness),
                        row(ScoreComponentName.POPULARITY, popularity)));
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
                    1.0d, 1.0d, ScoreComponentAvailability.NEUTRAL_FILLED, null, 0.5d, null);
        }
        return new ScoreComponentBreakdown(component, "scored", null, version,
                1.0d, 1.0d, ScoreComponentAvailability.SCORED,
                rawScore, rawScore, rawScore * 0.1d);
    }

    private static DiversityCandidateMetadata metadata(
            String id, RecommendationEntityType type, int index
    ) {
        String region = switch (index % 3) {
            case 0 -> "region:seoul";
            case 1 -> "region:busan";
            default -> "region:jeju";
        };
        return new DiversityCandidateMetadata(id, type, "author-" + (index % 7), region,
                index % 2 == 0 ? "theme:cafe" : "theme:nature", "dup:" + id);
    }

    private static ExplorationCandidateMetadata explorationMetadata(
            String id, RecommendationEntityType type, int index
    ) {
        DiversityCandidateMetadata base = metadata(id, type, index);
        return new ExplorationCandidateMetadata(base.entityId(), base.entityType(), base.authorId(),
                base.primaryRegionFeatureId(), base.primaryThemeFeatureId(), base.duplicateGroupId(),
                index == 40 ? 0 : 1);
    }

    record Scenario(
            RecommendationOfflineEvaluationCase evaluationCase,
            com.jc.recommendation.model.offline.OfflineEvaluationContracts.CollectedRankingV3Result collected,
            ReplayResult replay,
            AttributeRecommendationOutcomesResult attribution,
            PolicyVector treatment,
            PolicyComparisonResult comparison
    ) {
    }
}
