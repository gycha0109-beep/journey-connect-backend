package com.jc.recommendation.foundation;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.canonical.JsonWire;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesResult;
import com.jc.recommendation.model.evaluation.RecommendationOutcomeAttribution;
import com.jc.recommendation.model.evaluation.RecommendationOutcomeAttributionAudit;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.DecisionInput;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.DecisionResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailMetric;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailOperator;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailRule;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailSet;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyComparisonResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayMessageCode;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayMismatch;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayMismatchCategory;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayStatus;
import com.jc.recommendation.offline.RecommendationEvaluationDecider;
import com.jc.recommendation.policy.OfflineEvaluationPolicies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreWave7OfflineEvaluationGoldenOracle {
    private final List<Map<String, Object>> records = new ArrayList<>();

    public static void main(String[] args) {
        new CoreWave7OfflineEvaluationGoldenOracle().run();
    }

    private void run() {
        Wave7OfflineEvaluationFixture.Scenario scenario = Wave7OfflineEvaluationFixture.scenario();
        var policy = OfflineEvaluationPolicies.V1;
        emit("POLICY", policy.policyVersion(), join(policy.cutoffs()), policy.evaluatorPageSize(),
                policy.maximumReplayInputCandidates(), policy.maximumCollectorPageCount(),
                policy.minimumReplayCaseCount(), policy.minimumAttributedOutcomeEventCount(),
                bits(policy.minimumCommonSupportCoverageAt10()), bits(policy.exactReplayRequiredRate()));
        emit("COLLECT", scenario.collected().pages().size(), scenario.collected().finalCandidates().size(),
                scenario.collected().terminalCandidates().size(),
                String.join(",", scenario.collected().invariantViolations().stream().map(Enum::name).toList()),
                scenario.collected().finalCandidates().getFirst().entityId(),
                scenario.collected().finalCandidates().getLast().entityId());
        ReplayResult replay = scenario.replay();
        emit("REPLAY", replay.status().wireValue(), replay.observedExposureEventCount(),
                replay.exactExposureEventCount(), replay.mismatchedExposureEventCount(),
                String.join(",", replay.coverage().intervals().stream()
                        .map(item -> item.startRank() + "-" + item.endRank()).toList()),
                replay.coverage().coveredRankCount(), replay.coverage().totalRankCount(),
                bits(replay.coverage().coverageRate()), replay.coverage().fullObservation(),
                replay.reconstructedFinalCandidateCount(), replay.reconstructedTerminalCandidateCount(),
                replay.mismatches().size(), replay.invariantViolations().size());
        AttributeRecommendationOutcomesResult attribution = scenario.attribution();
        emit("ATTRIBUTION", attribution.attributedOutcomeEventCount(),
                attribution.attributedNumericEventCount(), bits(attribution.associatedOutcomeValue()),
                attribution.clickCount(), attribution.positiveEventCount(), attribution.negativeEventCount(),
                attribution.severeReportCount());
        PolicyComparisonResult comparison = scenario.comparison();
        emit("COMPARISON", comparison.comparisonMode().wireValue(),
                comparison.baselineReplayStatus().wireValue(), comparison.supportScope().wireValue(),
                comparison.baselineFinalRankedCandidateCount(), comparison.treatmentFinalRankedCandidateCount(),
                comparison.baselineExplorationInsertionCount(), comparison.treatmentExplorationInsertionCount(),
                String.join(",", comparison.invariantViolations().stream().map(Enum::name).toList()));
        for (var metric : comparison.metricsAtCutoffs()) {
            emit("METRIC", metric.cutoff(), metric.intersectionCount(), metric.unionCount(),
                    bits(metric.topKJaccard()), bits(metric.topKOverlapRate()),
                    nullable(metric.meanAbsoluteRankShiftAtK()), nullable(metric.maxAbsoluteRankShiftAtK()),
                    metric.baselineOriginMetrics().personalizedOriginShareAtK() == null ? "null"
                            : bits(metric.baselineOriginMetrics().personalizedOriginShareAtK()),
                    metric.treatmentOriginMetrics().explorationOriginShareAtK() == null ? "null"
                            : bits(metric.treatmentOriginMetrics().explorationOriginShareAtK()),
                    metric.baselineDiversityMetrics().uniqueAuthorCountAtK(),
                    metric.treatmentDiversityMetrics().uniqueAuthorCountAtK(),
                    metric.observedSupportMetrics().supportedTreatmentCandidateCountAtK(),
                    metric.observedSupportMetrics().treatmentCandidateCountAtK(),
                    bits(metric.observedSupportMetrics().commonSupportCoverageAtK()),
                    metric.observedSupportMetrics().attributedOutcomeEventCountAtK(),
                    bits(metric.observedSupportMetrics().associatedOutcomeValueAtK()),
                    metric.observedSupportMetrics().severeReportCountDeltaAtK());
        }

        List<CaseCopy> copies = new ArrayList<>();
        for (int index = 1; index <= 30; index++) copies.add(copyCase(scenario, index));
        List<ReplayResult> replayResults = copies.stream().map(CaseCopy::replay).toList();
        List<AttributeRecommendationOutcomesResult> attributionResults = copies.stream()
                .map(CaseCopy::attribution).toList();
        List<PolicyComparisonResult> comparisonResults = copies.stream().map(CaseCopy::comparison).toList();
        RecommendationEvaluationDecider decider = new RecommendationEvaluationDecider();
        DecisionResult insufficient = decider.decide(new DecisionInput(
                List.of(replay), List.of(attribution), List.of(comparison), null));
        DecisionResult pass = decider.decide(new DecisionInput(
                replayResults, attributionResults, comparisonResults, null));
        DecisionResult review = decider.decide(new DecisionInput(
                replayResults, attributionResults, comparisonResults,
                new GuardrailSet("guardrail-wave7", List.of(new GuardrailRule(
                        "overlap-min", GuardrailMetric.TOP_K_OVERLAP_RATE, 10,
                        GuardrailOperator.MINIMUM, 1.1d)))));
        ReplayResult first = replayResults.getFirst();
        ReplayResult mismatch = new ReplayResult(
                first.caseId(), first.recommendationRunId(), first.replayKey(), ReplayStatus.MISMATCH,
                first.observedExposureEventCount(), 0, 1, first.coverage(),
                List.of(new ReplayMismatch("exp-wave7-1", ReplayMismatchCategory.FINGERPRINT,
                        null, ReplayMessageCode.FINGERPRINT_MISMATCH)),
                first.invariantViolations(), first.reconstructedFinalCandidateCount(),
                first.reconstructedTerminalCandidateCount());
        List<ReplayResult> blockedReplay = new ArrayList<>(replayResults);
        blockedReplay.set(0, mismatch);
        DecisionResult block = decider.decide(new DecisionInput(
                blockedReplay, attributionResults.subList(1, attributionResults.size()),
                comparisonResults.subList(1, comparisonResults.size()), null));
        decision("insufficient", insufficient);
        decision("pass", pass);
        decision("review", review);
        decision("block", block);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fixtureVersion", "wave7-offline-evaluation-v1");
        document.put("referencePackage", "yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0");
        document.put("records", records);
        try {
            System.out.write((CanonicalJson.stringify(document) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Wave 7 golden", exception);
        }
    }

    private static CaseCopy copyCase(Wave7OfflineEvaluationFixture.Scenario scenario, int index) {
        String caseId = "case-wave7-" + index;
        String runId = "run-wave7-" + index;
        String replayKey = "replay-wave7-" + index;
        ReplayResult sourceReplay = scenario.replay();
        ReplayResult replay = new ReplayResult(
                caseId, runId, replayKey, sourceReplay.status(), sourceReplay.observedExposureEventCount(),
                sourceReplay.exactExposureEventCount(), sourceReplay.mismatchedExposureEventCount(),
                sourceReplay.coverage(), sourceReplay.mismatches(), sourceReplay.invariantViolations(),
                sourceReplay.reconstructedFinalCandidateCount(), sourceReplay.reconstructedTerminalCandidateCount());
        AttributeRecommendationOutcomesResult sourceAttribution = scenario.attribution();
        List<RecommendationOutcomeAttribution> attributions = sourceAttribution.attributions().stream()
                .map(item -> new RecommendationOutcomeAttribution(
                        item.behaviorEventId() + "-" + index, item.behaviorEventType(), runId,
                        item.exposureEventId() + "-" + index, item.entityType(), item.entityId(),
                        item.exposureServedAt(), item.behaviorOccurredAt(), item.elapsedMs(),
                        item.attributionWindowMs(), item.associatedOutcomeValue(), item.isPositive(),
                        item.isNegative(), item.isSevereReport())).toList();
        List<RecommendationOutcomeAttributionAudit> audits = sourceAttribution.audits().stream()
                .map(item -> new RecommendationOutcomeAttributionAudit(
                        item.behaviorEventId() + "-" + index, item.category(),
                        item.recommendationRunId() == null ? null : runId, item.entityId(),
                        item.resolvedEntityType(), item.exposureEventId() == null
                        ? null : item.exposureEventId() + "-" + index)).toList();
        AttributeRecommendationOutcomesResult attribution = new AttributeRecommendationOutcomesResult(
                caseId, runId, replayKey, sourceAttribution.resolvedBehaviorEventCount(),
                sourceAttribution.attributedOutcomeEventCount(), sourceAttribution.attributedNumericEventCount(),
                sourceAttribution.associatedOutcomeValue(), sourceAttribution.clickCount(),
                sourceAttribution.positiveEventCount(), sourceAttribution.negativeEventCount(),
                sourceAttribution.severeReportCount(), sourceAttribution.ambiguousOutcomeEventCount(),
                sourceAttribution.unmatchedOutcomeEventCount(), sourceAttribution.runUserSessionMismatchCount(),
                sourceAttribution.auditCounts(), attributions, audits);
        PolicyComparisonResult sourceComparison = scenario.comparison();
        PolicyComparisonResult comparison = new PolicyComparisonResult(
                caseId, runId, replayKey, sourceComparison.comparisonMode(),
                sourceComparison.baselinePolicyVector(), sourceComparison.treatmentPolicyVector(),
                sourceComparison.baselineReplayStatus(), sourceComparison.supportScope(),
                sourceComparison.baselineFinalRankedCandidateCount(),
                sourceComparison.treatmentFinalRankedCandidateCount(),
                sourceComparison.finalRankedCandidateCountDelta(),
                sourceComparison.baselineExplorationInsertionCount(),
                sourceComparison.treatmentExplorationInsertionCount(),
                sourceComparison.explorationInsertionCountDelta(), sourceComparison.baselineTerminalCount(),
                sourceComparison.treatmentTerminalCount(), sourceComparison.terminalCountDelta(),
                sourceComparison.metricsAtCutoffs(), sourceComparison.globalAttributionQuality(),
                sourceComparison.invariantViolations());
        return new CaseCopy(replay, attribution, comparison);
    }

    private void decision(String name, DecisionResult result) {
        emit("DECISION", name, result.decision().wireValue(),
                result.guardrailSetId() == null ? "null" : result.guardrailSetId(),
                result.aggregateEvidence().replayCaseCount(), result.aggregateEvidence().exactReplayCaseCount(),
                bits(result.aggregateEvidence().exactReplayRate()),
                result.aggregateEvidence().attributedOutcomeEventCount(),
                result.aggregateEvidence().commonSupportNumeratorAt10(),
                result.aggregateEvidence().commonSupportDenominatorAt10(),
                bits(result.aggregateEvidence().aggregateCommonSupportCoverageAt10()),
                String.join(",", result.blockReasons().stream().map(Enum::name).toList()),
                String.join(",", result.evidenceFailures().stream().map(Enum::name).toList()),
                String.join(",", result.reviewBreaches().stream().map(item ->
                        item.caseId() + ":" + item.ruleId() + ":" + bits(item.observedValue())).toList()));
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

    private static String join(List<Integer> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    private record CaseCopy(
            ReplayResult replay,
            AttributeRecommendationOutcomesResult attribution,
            PolicyComparisonResult comparison
    ) {
    }
}
