package com.jc.recommendation.foundation;

import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CaseEvaluationResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.DecisionInput;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RecommendationOfflineEvaluationCase;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.EvaluationDecision;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayStatus;
import com.jc.recommendation.offline.RecommendationEvaluationDecider;
import com.jc.recommendation.offline.RecommendationOfflineEvaluationEngine;
import com.jc.recommendation.policy.OfflineEvaluationPolicies;

import java.util.List;

public final class CoreWave7OfflineEvaluationContractTest {
    public static void main(String[] args) {
        Wave7OfflineEvaluationFixture.Scenario scenario = Wave7OfflineEvaluationFixture.scenario();
        check(scenario.collected().pages().size() == 2, "cursor exhaustion collection");
        check(scenario.collected().invariantViolations().isEmpty(), "collector invariants");
        check(scenario.replay().status() == ReplayStatus.EXACT_MATCH, "exact replay");
        check(scenario.replay().coverage().fullObservation(), "full observation");
        check(scenario.comparison().metricsAtCutoffs().size() == 3, "cutoff metrics");
        check(scenario.comparison().invariantViolations().isEmpty(), "comparison invariants");

        RecommendationOfflineEvaluationEngine engine = new RecommendationOfflineEvaluationEngine();
        CaseEvaluationResult exact = engine.evaluateCase(scenario.evaluationCase(), scenario.treatment());
        check(exact.replayResult().equals(scenario.replay()), "orchestrator exact replay");
        check(exact.attributionResult().equals(scenario.attribution()), "orchestrator attribution");
        check(exact.comparisonResult().equals(scenario.comparison()), "orchestrator comparison");

        RecommendationOfflineEvaluationCase partialCase = new RecommendationOfflineEvaluationCase(
                "case-wave7-partial", scenario.evaluationCase().rankingInputSnapshot(),
                List.of(scenario.evaluationCase().exposureEvents().getFirst()),
                scenario.evaluationCase().behaviorEvents(), scenario.evaluationCase().evaluationCutoffAt());
        CaseEvaluationResult partial = engine.evaluateCase(partialCase, scenario.treatment());
        check(partial.replayResult().status() == ReplayStatus.PARTIAL_OBSERVATION,
                "orchestrator partial replay");
        check(partial.attributionResult() != null && partial.comparisonResult() != null,
                "partial replay continues downstream evaluation");

        RecommendationExposureEventV1 mismatchedExposure = withPageFingerprint(
                scenario.evaluationCase().exposureEvents().getFirst(), "0".repeat(64));
        RecommendationOfflineEvaluationCase mismatchCase = new RecommendationOfflineEvaluationCase(
                "case-wave7-mismatch", scenario.evaluationCase().rankingInputSnapshot(),
                List.of(mismatchedExposure), scenario.evaluationCase().behaviorEvents(),
                scenario.evaluationCase().evaluationCutoffAt());
        CaseEvaluationResult mismatch = engine.evaluateCase(mismatchCase, scenario.treatment());
        check(mismatch.replayResult().status() != ReplayStatus.EXACT_MATCH
                        && mismatch.replayResult().status() != ReplayStatus.PARTIAL_OBSERVATION,
                "orchestrator invalid or mismatched replay");
        check(mismatch.attributionResult() == null && mismatch.comparisonResult() == null,
                "mismatch blocks downstream evaluation");
        var decision = new RecommendationEvaluationDecider().decide(new DecisionInput(
                List.of(scenario.replay()), List.of(scenario.attribution()),
                List.of(scenario.comparison()), null));
        check(decision.decision() == EvaluationDecision.INSUFFICIENT_EVIDENCE,
                "evidence threshold decision");
        check(OfflineEvaluationPolicies.V1.cutoffs().equals(List.of(5, 10, 20)), "policy cutoffs");
        System.out.println("Java recommendation core Wave 7 offline evaluation contract: PASS");
    }

    private static RecommendationExposureEventV1 withPageFingerprint(
            RecommendationExposureEventV1 source,
            String pageFingerprint
    ) {
        return new RecommendationExposureEventV1(
                source.schemaVersion(), source.eventId(), source.idempotencyKey(),
                source.recommendationRunId(), source.userId(), source.sessionId(), source.contextId(),
                source.surface(), source.servedAt(), source.replayKey(), pageFingerprint,
                source.cursorVersion(), source.rankingSnapshotId(), source.metadataSnapshotId(),
                source.explorationSnapshotId(), source.rankingPolicyVersion(),
                source.baseIntegrationPolicyVersion(), source.baseRankingPolicyVersion(),
                source.scorePolicyVersion(), source.componentPolicyVersions(),
                source.diversityPolicyVersion(), source.explorationPolicyVersion(),
                source.explorationSeed(), source.rankingStatus(), source.rankingEmptyReason(),
                source.requestedLimit(), source.effectiveLimit(), source.pageStartRank(),
                source.pageEndRank(), source.pageCandidateCount(), source.hasNextPage(),
                source.inputCount(), source.finalRankedCandidateCount(), source.terminalCandidateCount(),
                source.diversitySummary(), source.explorationSummary(), source.candidates());
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
