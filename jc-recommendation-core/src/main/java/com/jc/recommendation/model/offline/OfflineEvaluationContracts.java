package com.jc.recommendation.model.offline;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimensionCounts;
import com.jc.recommendation.model.evaluation.AttributeRecommendationOutcomesResult;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.policy.ExplorationEnabledRankingPolicy;
import com.jc.recommendation.policy.ExplorationPolicy;

import java.util.List;
import java.util.Objects;

public final class OfflineEvaluationContracts {
    private OfflineEvaluationContracts() {
    }

    public record RankingV3ReplayInputSnapshot(
            String rankingSnapshotId,
            String metadataSnapshotId,
            String explorationSnapshotId,
            String userId,
            String contextId,
            String scorePolicyVersion,
            ScoreComponentPolicyVersions componentPolicyVersions,
            String explorationSeed,
            List<CandidateScoreResult> candidates,
            List<DiversityCandidateMetadata> candidateMetadata,
            List<ExplorationCandidateMetadata> explorationMetadata,
            ExplorationEnabledRankingPolicy policy,
            DiversityPolicy diversityPolicy,
            ExplorationPolicy explorationPolicy
    ) {
        public RankingV3ReplayInputSnapshot {
            Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
            Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
            Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(contextId, "contextId");
            Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
            Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
            Objects.requireNonNull(explorationSeed, "explorationSeed");
            candidates = List.copyOf(candidates);
            candidateMetadata = List.copyOf(candidateMetadata);
            explorationMetadata = List.copyOf(explorationMetadata);
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(diversityPolicy, "diversityPolicy");
            Objects.requireNonNull(explorationPolicy, "explorationPolicy");
        }
    }

    public record RecommendationOfflineEvaluationCase(
            String caseId,
            RankingV3ReplayInputSnapshot rankingInputSnapshot,
            List<RecommendationExposureEventV1> exposureEvents,
            List<UserBehaviorEvent> behaviorEvents,
            String evaluationCutoffAt
    ) {
        public RecommendationOfflineEvaluationCase {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(rankingInputSnapshot, "rankingInputSnapshot");
            exposureEvents = List.copyOf(exposureEvents);
            behaviorEvents = List.copyOf(behaviorEvents);
            Objects.requireNonNull(evaluationCutoffAt, "evaluationCutoffAt");
        }
    }

    public enum ReplayStatus {
        INVALID_TRACE("invalid_trace"),
        INVALID_SNAPSHOT("invalid_snapshot"),
        MISMATCH("mismatch"),
        PARTIAL_OBSERVATION("partial_observation"),
        EXACT_MATCH("exact_match");

        private final String wireValue;

        ReplayStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public enum ReplayMismatchCategory {
        SNAPSHOT_BINDING("snapshot_binding"), POLICY_BINDING("policy_binding"),
        SEED_BINDING("seed_binding"), STATUS("status"), COUNT("count"), SUMMARY("summary"),
        PAGE_BOUNDARY("page_boundary"), CANDIDATE_IDENTITY("candidate_identity"),
        CANDIDATE_RANK("candidate_rank"), CANDIDATE_ORIGIN("candidate_origin"),
        CANDIDATE_PROVENANCE("candidate_provenance"), SIGNED_ZERO("signed_zero"),
        FINGERPRINT("fingerprint"), CURSOR_CHAIN("cursor_chain");

        private final String wireValue;

        ReplayMismatchCategory(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public enum ReplayMessageCode {
        SNAPSHOT_BINDING_MISMATCH,
        POLICY_BINDING_MISMATCH,
        SEED_BINDING_MISMATCH,
        STATUS_MISMATCH,
        COUNT_MISMATCH,
        SUMMARY_MISMATCH,
        PAGE_BOUNDARY_MISMATCH,
        CANDIDATE_IDENTITY_MISMATCH,
        CANDIDATE_RANK_MISMATCH,
        CANDIDATE_ORIGIN_MISMATCH,
        CANDIDATE_PROVENANCE_MISMATCH,
        SIGNED_ZERO_MISMATCH,
        FINGERPRINT_MISMATCH,
        CURSOR_CHAIN_MISMATCH
    }

    public enum InvariantViolationCode {
        COLLECTOR_CURSOR_LOOP,
        COLLECTOR_CURSOR_DISCONTINUITY,
        COLLECTOR_PAGE_LIMIT_EXCEEDED,
        COLLECTOR_BINDING_DRIFT,
        COLLECTOR_STATUS_DRIFT,
        COLLECTOR_PAGE_BOUNDARY_DISCONTINUITY,
        COLLECTOR_TERMINAL_AUDIT_DRIFT,
        DUPLICATE_FINAL_IDENTITY,
        ABSOLUTE_RANK_DISCONTINUITY,
        INPUT_PARTITION_MISMATCH,
        SYNTHETIC_FINAL_IDENTITY,
        PERSONALIZED_SCORE_MUTATION,
        PERSONALIZED_SIGNED_ZERO_MUTATION,
        EXPLORATION_SCORE_NON_NULL,
        SUMMARY_DRIFT,
        METADATA_RESOLUTION_FAILURE,
        COMPARISON_COLLECTOR_FAILURE
    }

    public record ReplayMismatch(
            String exposureEventId,
            ReplayMismatchCategory category,
            Integer absoluteRank,
            ReplayMessageCode messageCode
    ) {
        public ReplayMismatch {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(messageCode, "messageCode");
        }
    }

    public record ReplayCoverageInterval(int startRank, int endRank) {
    }

    public record ReplayCoverage(
            List<ReplayCoverageInterval> intervals,
            int coveredRankCount,
            int totalRankCount,
            double coverageRate,
            boolean fullObservation
    ) {
        public ReplayCoverage {
            intervals = List.copyOf(intervals);
        }
    }

    public record ReplayResult(
            String caseId,
            String recommendationRunId,
            String replayKey,
            ReplayStatus status,
            int observedExposureEventCount,
            int exactExposureEventCount,
            int mismatchedExposureEventCount,
            ReplayCoverage coverage,
            List<ReplayMismatch> mismatches,
            List<InvariantViolationCode> invariantViolations,
            Integer reconstructedFinalCandidateCount,
            Integer reconstructedTerminalCandidateCount
    ) {
        public ReplayResult {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(coverage, "coverage");
            mismatches = List.copyOf(mismatches);
            invariantViolations = List.copyOf(invariantViolations);
        }
    }

    public record PolicyVector(
            ExplorationEnabledRankingPolicy rankingPolicy,
            DiversityPolicy diversityPolicy,
            ExplorationPolicy explorationPolicy,
            String explorationSeed
    ) {
        public PolicyVector {
            Objects.requireNonNull(rankingPolicy, "rankingPolicy");
            Objects.requireNonNull(diversityPolicy, "diversityPolicy");
            Objects.requireNonNull(explorationPolicy, "explorationPolicy");
            Objects.requireNonNull(explorationSeed, "explorationSeed");
        }
    }

    public enum ComparisonMode {
        IDENTICAL_VECTOR("identical_vector"), POLICY_ONLY("policy_only"), POLICY_AND_SEED("policy_and_seed");
        private final String wireValue;
        ComparisonMode(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum ObservationSupportScope {
        FULL("full"), PARTIAL("partial");
        private final String wireValue;
        ObservationSupportScope(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public record OriginMetricsAtK(
            Double personalizedOriginShareAtK,
            Double explorationOriginShareAtK,
            Double meanPersonalizedScoreAtK,
            Double meanExplorationQualityAtK,
            Double meanExplorationRecentExposureCountAtK,
            Double zeroRecentExposureExplorationShareAtK
    ) {
    }

    public record DiversityMetricsAtK(
            int uniqueAuthorCountAtK,
            int uniqueRegionCountAtK,
            int uniqueThemeCountAtK,
            int duplicateGroupCollisionCountAtK,
            int diversityCapViolationCountAtK,
            DiversityDimensionCounts violationCountByDimensionAtK
    ) {
        public DiversityMetricsAtK {
            Objects.requireNonNull(violationCountByDimensionAtK, "violationCountByDimensionAtK");
        }
    }

    public record ObservedSupportMetricsAtK(
            int supportedTreatmentCandidateCountAtK,
            int treatmentCandidateCountAtK,
            double commonSupportCoverageAtK,
            int attributedOutcomeEventCountAtK,
            double associatedOutcomeValueAtK,
            int associatedClickCountAtK,
            int associatedPositiveEventCountAtK,
            int associatedNegativeEventCountAtK,
            int severeReportCountAtK,
            int baselineSevereReportCountAtK,
            int severeReportCountDeltaAtK
    ) {
    }

    public record TopKStructuralMetrics(
            int cutoff,
            int intersectionCount,
            int unionCount,
            double topKJaccard,
            double topKOverlapRate,
            int baselineListLength,
            int treatmentListLength,
            int listLengthDelta,
            Double meanAbsoluteRankShiftAtK,
            Double maxAbsoluteRankShiftAtK,
            OriginMetricsAtK baselineOriginMetrics,
            OriginMetricsAtK treatmentOriginMetrics,
            DiversityMetricsAtK baselineDiversityMetrics,
            DiversityMetricsAtK treatmentDiversityMetrics,
            int uniqueAuthorCountDelta,
            int uniqueRegionCountDelta,
            int uniqueThemeCountDelta,
            int duplicateGroupCollisionDelta,
            ObservedSupportMetricsAtK observedSupportMetrics
    ) {
        public TopKStructuralMetrics {
            Objects.requireNonNull(baselineOriginMetrics, "baselineOriginMetrics");
            Objects.requireNonNull(treatmentOriginMetrics, "treatmentOriginMetrics");
            Objects.requireNonNull(baselineDiversityMetrics, "baselineDiversityMetrics");
            Objects.requireNonNull(treatmentDiversityMetrics, "treatmentDiversityMetrics");
            Objects.requireNonNull(observedSupportMetrics, "observedSupportMetrics");
        }
    }

    public record GlobalAttributionQuality(
            int resolvedBehaviorEventCount,
            int attributedOutcomeEventCount,
            int ambiguousOutcomeEventCount,
            int unmatchedOutcomeEventCount,
            int runUserSessionMismatchCount,
            double ambiguousOutcomeRate,
            double unmatchedOutcomeRate
    ) {
    }

    public record CompareInput(
            RecommendationOfflineEvaluationCase evaluationCase,
            ReplayResult baselineReplayResult,
            PolicyVector treatmentPolicyVector,
            AttributeRecommendationOutcomesResult attributionResult
    ) {
        public CompareInput {
            Objects.requireNonNull(evaluationCase, "evaluationCase");
            Objects.requireNonNull(baselineReplayResult, "baselineReplayResult");
            Objects.requireNonNull(treatmentPolicyVector, "treatmentPolicyVector");
            Objects.requireNonNull(attributionResult, "attributionResult");
        }
    }

    public record PolicyComparisonResult(
            String caseId,
            String recommendationRunId,
            String replayKey,
            ComparisonMode comparisonMode,
            PolicyVector baselinePolicyVector,
            PolicyVector treatmentPolicyVector,
            ReplayStatus baselineReplayStatus,
            ObservationSupportScope supportScope,
            int baselineFinalRankedCandidateCount,
            int treatmentFinalRankedCandidateCount,
            int finalRankedCandidateCountDelta,
            int baselineExplorationInsertionCount,
            int treatmentExplorationInsertionCount,
            int explorationInsertionCountDelta,
            int baselineTerminalCount,
            int treatmentTerminalCount,
            int terminalCountDelta,
            List<TopKStructuralMetrics> metricsAtCutoffs,
            GlobalAttributionQuality globalAttributionQuality,
            List<InvariantViolationCode> invariantViolations
    ) {
        public PolicyComparisonResult {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(recommendationRunId, "recommendationRunId");
            Objects.requireNonNull(replayKey, "replayKey");
            Objects.requireNonNull(comparisonMode, "comparisonMode");
            Objects.requireNonNull(baselinePolicyVector, "baselinePolicyVector");
            Objects.requireNonNull(treatmentPolicyVector, "treatmentPolicyVector");
            Objects.requireNonNull(baselineReplayStatus, "baselineReplayStatus");
            Objects.requireNonNull(supportScope, "supportScope");
            metricsAtCutoffs = List.copyOf(metricsAtCutoffs);
            Objects.requireNonNull(globalAttributionQuality, "globalAttributionQuality");
            invariantViolations = List.copyOf(invariantViolations);
        }
    }

    public enum EvaluationDecision {
        BLOCK("block"), INSUFFICIENT_EVIDENCE("insufficient_evidence"), REVIEW("review"), PASS("pass");
        private final String wireValue;
        EvaluationDecision(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum GuardrailOperator {
        MINIMUM("minimum"), MAXIMUM("maximum");
        private final String wireValue;
        GuardrailOperator(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum GuardrailMetric {
        TOP_K_OVERLAP_RATE("top_k_overlap_rate"),
        LIST_LENGTH_DELTA("list_length_delta"),
        UNIQUE_AUTHOR_COUNT_DELTA("unique_author_count_delta"),
        UNIQUE_REGION_COUNT_DELTA("unique_region_count_delta"),
        UNIQUE_THEME_COUNT_DELTA("unique_theme_count_delta"),
        DUPLICATE_GROUP_COLLISION_DELTA("duplicate_group_collision_delta"),
        DIVERSITY_CAP_VIOLATION_COUNT("diversity_cap_violation_count"),
        EXPLORATION_ORIGIN_SHARE("exploration_origin_share"),
        ZERO_RECENT_EXPOSURE_EXPLORATION_SHARE("zero_recent_exposure_exploration_share"),
        SEVERE_REPORT_COUNT_DELTA("severe_report_count_delta"),
        AMBIGUOUS_OUTCOME_RATE("ambiguous_outcome_rate"),
        UNMATCHED_OUTCOME_RATE("unmatched_outcome_rate");

        private final String wireValue;
        GuardrailMetric(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
        public boolean global() {
            return this == AMBIGUOUS_OUTCOME_RATE || this == UNMATCHED_OUTCOME_RATE;
        }
    }

    public record GuardrailRule(
            String ruleId,
            GuardrailMetric metric,
            Integer cutoff,
            GuardrailOperator operator,
            double threshold
    ) {
        public GuardrailRule {
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(operator, "operator");
        }
    }

    public record GuardrailSet(String guardrailSetId, List<GuardrailRule> rules) {
        public GuardrailSet {
            Objects.requireNonNull(guardrailSetId, "guardrailSetId");
            rules = List.copyOf(rules);
        }
    }

    public record GuardrailBreach(
            String caseId,
            String recommendationRunId,
            String ruleId,
            GuardrailMetric metric,
            Integer cutoff,
            GuardrailOperator operator,
            double threshold,
            double observedValue
    ) {
        public GuardrailBreach {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(recommendationRunId, "recommendationRunId");
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(operator, "operator");
        }
    }

    public enum EvaluationBlockReason {
        INVALID_TRACE, INVALID_SNAPSHOT, REPLAY_MISMATCH, INVARIANT_VIOLATION
    }

    public enum EvidenceFailure {
        MINIMUM_REPLAY_CASE_COUNT,
        MINIMUM_ATTRIBUTED_OUTCOME_EVENT_COUNT,
        EXACT_REPLAY_REQUIRED_RATE,
        MINIMUM_COMMON_SUPPORT_COVERAGE_AT_10
    }

    public record AggregateEvidence(
            int replayCaseCount,
            int exactReplayCaseCount,
            double exactReplayRate,
            int attributedOutcomeEventCount,
            int commonSupportNumeratorAt10,
            int commonSupportDenominatorAt10,
            double aggregateCommonSupportCoverageAt10
    ) {
    }

    public record DecisionInput(
            List<ReplayResult> replayResults,
            List<AttributeRecommendationOutcomesResult> attributionResults,
            List<PolicyComparisonResult> comparisonResults,
            GuardrailSet guardrailSet
    ) {
        public DecisionInput {
            replayResults = List.copyOf(replayResults);
            attributionResults = List.copyOf(attributionResults);
            comparisonResults = List.copyOf(comparisonResults);
        }
    }

    public record DecisionResult(
            EvaluationDecision decision,
            String policyVersion,
            String guardrailSetId,
            AggregateEvidence aggregateEvidence,
            List<EvaluationBlockReason> blockReasons,
            List<EvidenceFailure> evidenceFailures,
            List<GuardrailBreach> reviewBreaches
    ) {
        public DecisionResult {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(policyVersion, "policyVersion");
            Objects.requireNonNull(aggregateEvidence, "aggregateEvidence");
            blockReasons = List.copyOf(blockReasons);
            evidenceFailures = List.copyOf(evidenceFailures);
            reviewBreaches = List.copyOf(reviewBreaches);
        }
    }

    public record CaseEvaluationResult(
            ReplayResult replayResult,
            AttributeRecommendationOutcomesResult attributionResult,
            PolicyComparisonResult comparisonResult
    ) {
        public CaseEvaluationResult {
            Objects.requireNonNull(replayResult, "replayResult");
            if ((attributionResult == null) != (comparisonResult == null)) {
                throw new IllegalArgumentException("downstream results must be both present or both absent");
            }
        }
    }

    public record CollectedRankingV3Result(
            List<RankCandidatesWithExplorationResult> pages,
            List<ExplorationFinalCandidate> finalCandidates,
            List<TerminalCandidateAudit> terminalCandidates,
            RankCandidatesWithExplorationResult firstPage,
            List<InvariantViolationCode> invariantViolations
    ) {
        public CollectedRankingV3Result {
            pages = List.copyOf(pages);
            finalCandidates = List.copyOf(finalCandidates);
            terminalCandidates = List.copyOf(terminalCandidates);
            Objects.requireNonNull(firstPage, "firstPage");
            invariantViolations = List.copyOf(invariantViolations);
        }
    }
}
