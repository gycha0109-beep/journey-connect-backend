package com.jc.recommendation.offline;

import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.AggregateEvidence;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.DecisionInput;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.DecisionResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.EvaluationBlockReason;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.EvaluationDecision;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.EvidenceFailure;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailBreach;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailMetric;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailOperator;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailRule;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.GuardrailSet;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.PolicyComparisonResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayStatus;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.TopKStructuralMetrics;
import com.jc.recommendation.policy.OfflineEvaluationPolicies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationEvaluationDecider {
    public DecisionResult decide(DecisionInput input) {
        validateGuardrail(input.guardrailSet());
        validateUniqueCases(input);
        Map<String, ReplayResult> replayByCase = new HashMap<>();
        for (ReplayResult replay : input.replayResults()) replayByCase.put(replay.caseId(), replay);
        Map<String, AttributeRecommendationOutcomesResult> attributionByCase = new HashMap<>();
        for (AttributeRecommendationOutcomesResult attribution : input.attributionResults()) {
            attributionByCase.put(attribution.caseId(), attribution);
            ReplayResult replay = replayByCase.get(attribution.caseId());
            if (replay == null
                    || !java.util.Objects.equals(replay.recommendationRunId(), attribution.recommendationRunId())
                    || !java.util.Objects.equals(replay.replayKey(), attribution.replayKey())) {
                invalid("replay/attribution binding mismatch");
            }
        }
        for (PolicyComparisonResult comparison : input.comparisonResults()) {
            ReplayResult replay = replayByCase.get(comparison.caseId());
            AttributeRecommendationOutcomesResult attribution = attributionByCase.get(comparison.caseId());
            if (replay == null || attribution == null) invalid("comparison dependency missing");
            if (replay.status() != comparison.baselineReplayStatus()) invalid("comparison replay status mismatch");
            if (!java.util.Objects.equals(replay.recommendationRunId(), comparison.recommendationRunId())
                    || !java.util.Objects.equals(replay.replayKey(), comparison.replayKey())) {
                invalid("replay/comparison binding mismatch");
            }
            if (!attribution.recommendationRunId().equals(comparison.recommendationRunId())
                    || !attribution.replayKey().equals(comparison.replayKey())) {
                invalid("attribution/comparison binding mismatch");
            }
            var quality = comparison.globalAttributionQuality();
            int denominator = attribution.resolvedBehaviorEventCount();
            if (quality.resolvedBehaviorEventCount() != denominator
                    || quality.attributedOutcomeEventCount() != attribution.attributedOutcomeEventCount()
                    || quality.ambiguousOutcomeEventCount() != attribution.ambiguousOutcomeEventCount()
                    || quality.unmatchedOutcomeEventCount() != attribution.unmatchedOutcomeEventCount()
                    || quality.runUserSessionMismatchCount() != attribution.runUserSessionMismatchCount()) {
                invalid("attribution/comparison quality mismatch");
            }
            double expectedAmbiguous = denominator == 0 ? 0.0
                    : (double) attribution.ambiguousOutcomeEventCount() / denominator;
            double expectedUnmatched = denominator == 0 ? 0.0
                    : (double) attribution.unmatchedOutcomeEventCount() / denominator;
            if (quality.ambiguousOutcomeRate() != expectedAmbiguous
                    || quality.unmatchedOutcomeRate() != expectedUnmatched) {
                invalid("attribution/comparison rate mismatch");
            }
        }
        validateDownstreamCoverage(input);
        validateRunAndBehaviorUniqueness(input);

        List<EvaluationBlockReason> blockReasons = new ArrayList<>();
        if (input.replayResults().stream().anyMatch(item -> item.status() == ReplayStatus.INVALID_TRACE)) {
            blockReasons.add(EvaluationBlockReason.INVALID_TRACE);
        }
        if (input.replayResults().stream().anyMatch(item -> item.status() == ReplayStatus.INVALID_SNAPSHOT)) {
            blockReasons.add(EvaluationBlockReason.INVALID_SNAPSHOT);
        }
        if (input.replayResults().stream().anyMatch(item -> item.status() == ReplayStatus.MISMATCH)) {
            blockReasons.add(EvaluationBlockReason.REPLAY_MISMATCH);
        }
        if (input.replayResults().stream().anyMatch(item -> !item.invariantViolations().isEmpty())
                || input.comparisonResults().stream().anyMatch(item -> !item.invariantViolations().isEmpty())) {
            blockReasons.add(EvaluationBlockReason.INVARIANT_VIOLATION);
        }

        int replayCaseCount = input.replayResults().size();
        int exactReplayCaseCount = (int) input.replayResults().stream()
                .filter(item -> item.status() == ReplayStatus.EXACT_MATCH).count();
        double exactReplayRate = replayCaseCount == 0 ? 0.0
                : (double) exactReplayCaseCount / replayCaseCount;
        int attributedOutcomeEventCount = input.attributionResults().stream()
                .mapToInt(AttributeRecommendationOutcomesResult::attributedOutcomeEventCount).sum();
        int commonSupportNumeratorAt10 = 0;
        int commonSupportDenominatorAt10 = 0;
        for (PolicyComparisonResult comparison : input.comparisonResults()) {
            TopKStructuralMetrics metric = metricAt(comparison, 10);
            commonSupportNumeratorAt10 += metric.observedSupportMetrics()
                    .supportedTreatmentCandidateCountAtK();
            commonSupportDenominatorAt10 += metric.observedSupportMetrics().treatmentCandidateCountAtK();
        }
        double aggregateCommonSupportCoverageAt10 = commonSupportDenominatorAt10 == 0 ? 1.0
                : (double) commonSupportNumeratorAt10 / commonSupportDenominatorAt10;
        AggregateEvidence aggregateEvidence = new AggregateEvidence(
                replayCaseCount, exactReplayCaseCount, exactReplayRate, attributedOutcomeEventCount,
                commonSupportNumeratorAt10, commonSupportDenominatorAt10,
                aggregateCommonSupportCoverageAt10);

        List<EvidenceFailure> evidenceFailures = new ArrayList<>();
        var policy = OfflineEvaluationPolicies.V1;
        if (replayCaseCount < policy.minimumReplayCaseCount()) {
            evidenceFailures.add(EvidenceFailure.MINIMUM_REPLAY_CASE_COUNT);
        }
        if (attributedOutcomeEventCount < policy.minimumAttributedOutcomeEventCount()) {
            evidenceFailures.add(EvidenceFailure.MINIMUM_ATTRIBUTED_OUTCOME_EVENT_COUNT);
        }
        if (exactReplayRate < policy.exactReplayRequiredRate()) {
            evidenceFailures.add(EvidenceFailure.EXACT_REPLAY_REQUIRED_RATE);
        }
        if (aggregateCommonSupportCoverageAt10 < policy.minimumCommonSupportCoverageAt10()) {
            evidenceFailures.add(EvidenceFailure.MINIMUM_COMMON_SUPPORT_COVERAGE_AT_10);
        }

        List<GuardrailBreach> reviewBreaches = evaluateGuardrails(
                input.comparisonResults(), input.guardrailSet());
        EvaluationDecision decision = !blockReasons.isEmpty() ? EvaluationDecision.BLOCK
                : !evidenceFailures.isEmpty() ? EvaluationDecision.INSUFFICIENT_EVIDENCE
                : !reviewBreaches.isEmpty() ? EvaluationDecision.REVIEW
                : EvaluationDecision.PASS;
        return new DecisionResult(
                decision, policy.policyVersion(),
                input.guardrailSet() == null ? null : input.guardrailSet().guardrailSetId(),
                aggregateEvidence, blockReasons, evidenceFailures, reviewBreaches);
    }

    private static List<GuardrailBreach> evaluateGuardrails(
            List<PolicyComparisonResult> comparisons,
            GuardrailSet guardrailSet
    ) {
        if (guardrailSet == null) return List.of();
        List<PolicyComparisonResult> sortedComparisons = comparisons.stream()
                .sorted(Comparator.comparing(PolicyComparisonResult::caseId)).toList();
        List<GuardrailRule> sortedRules = guardrailSet.rules().stream()
                .sorted(Comparator.comparing(GuardrailRule::ruleId)).toList();
        List<GuardrailBreach> breaches = new ArrayList<>();
        for (PolicyComparisonResult comparison : sortedComparisons) {
            for (GuardrailRule rule : sortedRules) {
                double value = observedValue(comparison, rule);
                boolean breached = rule.operator() == GuardrailOperator.MINIMUM
                        ? value < rule.threshold() : value > rule.threshold();
                if (breached) {
                    breaches.add(new GuardrailBreach(
                            comparison.caseId(), comparison.recommendationRunId(), rule.ruleId(),
                            rule.metric(), rule.cutoff(), rule.operator(), rule.threshold(), value));
                }
            }
        }
        return List.copyOf(breaches);
    }

    private static double observedValue(PolicyComparisonResult comparison, GuardrailRule rule) {
        if (rule.metric() == GuardrailMetric.AMBIGUOUS_OUTCOME_RATE) {
            return comparison.globalAttributionQuality().ambiguousOutcomeRate();
        }
        if (rule.metric() == GuardrailMetric.UNMATCHED_OUTCOME_RATE) {
            return comparison.globalAttributionQuality().unmatchedOutcomeRate();
        }
        TopKStructuralMetrics metric = metricAt(comparison, rule.cutoff());
        Double value = switch (rule.metric()) {
            case TOP_K_OVERLAP_RATE -> metric.topKOverlapRate();
            case LIST_LENGTH_DELTA -> (double) metric.listLengthDelta();
            case UNIQUE_AUTHOR_COUNT_DELTA -> (double) metric.uniqueAuthorCountDelta();
            case UNIQUE_REGION_COUNT_DELTA -> (double) metric.uniqueRegionCountDelta();
            case UNIQUE_THEME_COUNT_DELTA -> (double) metric.uniqueThemeCountDelta();
            case DUPLICATE_GROUP_COLLISION_DELTA -> (double) metric.duplicateGroupCollisionDelta();
            case DIVERSITY_CAP_VIOLATION_COUNT ->
                    (double) metric.treatmentDiversityMetrics().diversityCapViolationCountAtK();
            case EXPLORATION_ORIGIN_SHARE -> metric.treatmentOriginMetrics().explorationOriginShareAtK();
            case ZERO_RECENT_EXPOSURE_EXPLORATION_SHARE ->
                    metric.treatmentOriginMetrics().zeroRecentExposureExplorationShareAtK();
            case SEVERE_REPORT_COUNT_DELTA ->
                    (double) metric.observedSupportMetrics().severeReportCountDeltaAtK();
            case AMBIGUOUS_OUTCOME_RATE, UNMATCHED_OUTCOME_RATE -> throw new IllegalStateException();
        };
        if (value == null) invalid("guardrail metric is not applicable");
        return value;
    }

    private static TopKStructuralMetrics metricAt(PolicyComparisonResult comparison, Integer cutoff) {
        return comparison.metricsAtCutoffs().stream().filter(item -> item.cutoff() == cutoff)
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "INVALID_EVALUATION_DECISION_INPUT: missing cutoff metric"));
    }

    private static void validateGuardrail(GuardrailSet value) {
        if (value == null) return;
        if (value.guardrailSetId().isBlank()) invalid("guardrailSetId");
        Set<String> ruleIds = new HashSet<>();
        for (GuardrailRule rule : value.rules()) {
            if (rule.ruleId().isBlank() || !ruleIds.add(rule.ruleId())) invalid("duplicate ruleId");
            if (!Double.isFinite(rule.threshold())) invalid("guardrail threshold");
            if (rule.metric().global()) {
                if (rule.cutoff() != null) invalid("global metric cutoff must be null");
            } else if (rule.cutoff() == null
                    || (rule.cutoff() != 5 && rule.cutoff() != 10 && rule.cutoff() != 20)) {
                invalid("top-k metric cutoff is required");
            }
        }
    }

    private static void validateUniqueCases(DecisionInput input) {
        unique(input.replayResults().stream().map(ReplayResult::caseId).toList(), "replay caseId");
        unique(input.attributionResults().stream()
                .map(AttributeRecommendationOutcomesResult::caseId).toList(), "attribution caseId");
        unique(input.comparisonResults().stream()
                .map(PolicyComparisonResult::caseId).toList(), "comparison caseId");
    }

    private static void validateDownstreamCoverage(DecisionInput input) {
        Set<String> usableReplayCases = new HashSet<>();
        for (ReplayResult replay : input.replayResults()) {
            if (replay.status() == ReplayStatus.EXACT_MATCH
                    || replay.status() == ReplayStatus.PARTIAL_OBSERVATION) {
                usableReplayCases.add(replay.caseId());
            }
        }
        Set<String> attributionCases = new HashSet<>(input.attributionResults().stream()
                .map(AttributeRecommendationOutcomesResult::caseId).toList());
        Set<String> comparisonCases = new HashSet<>(input.comparisonResults().stream()
                .map(PolicyComparisonResult::caseId).toList());
        if (!usableReplayCases.equals(attributionCases) || !usableReplayCases.equals(comparisonCases)) {
            invalid("usable replay case coverage mismatch");
        }
    }

    private static void validateRunAndBehaviorUniqueness(DecisionInput input) {
        List<String> runIds = input.replayResults().stream()
                .map(ReplayResult::recommendationRunId).filter(java.util.Objects::nonNull).toList();
        unique(runIds, "recommendationRunId");
        Set<String> behaviorEventIds = new HashSet<>();
        for (AttributeRecommendationOutcomesResult result : input.attributionResults()) {
            for (var audit : result.audits()) {
                if (!behaviorEventIds.add(audit.behaviorEventId())) {
                    invalid("behaviorEventId reused across cases");
                }
            }
        }
    }

    private static void unique(List<String> values, String label) {
        if (new HashSet<>(values).size() != values.size()) invalid("duplicate " + label);
    }

    private static void invalid(String detail) {
        throw new IllegalArgumentException("INVALID_EVALUATION_DECISION_INPUT: " + detail);
    }
}
