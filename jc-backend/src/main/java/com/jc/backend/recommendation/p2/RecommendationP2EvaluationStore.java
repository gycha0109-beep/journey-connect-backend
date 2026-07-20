package com.jc.backend.recommendation.p2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import com.jc.backend.recommendation.persistence.RecommendationHashing;
import com.jc.recommendation.p2.P2EvaluationContracts.EvaluationInput;
import com.jc.recommendation.p2.P2EvaluationContracts.EvaluationResult;
import com.jc.recommendation.p2.P2EvaluationContracts.GateResult;
import com.jc.recommendation.p2.P2EvaluationContracts.MetricDefinition;
import com.jc.recommendation.p2.P2EvaluationContracts.MetricResult;
import com.jc.recommendation.p2.P2EvaluationContracts.SegmentResult;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationP2EvaluationStore {

    private static final String INSERT_EVALUATION =
            "insert into public.recommendation_p2_evaluation_run(" +
                    "evaluation_run_id,dataset_snapshot_id,metric_definition_version," +
                    "evaluation_policy_version,experiment_id,experiment_version," +
                    "baseline_policy_version,treatment_policy_version,evaluator_build_id," +
                    "evaluated_at,current_state,requested_state,operational_approval," +
                    "final_decision,target_state,evaluation_fingerprint" +
                    ") values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_METRIC_DEFINITION =
            "insert into public.recommendation_p2_metric_definition(" +
                    "metric_definition_version,metric_id,metric_role,direction,minimum_effect," +
                    "maximum_allowed_regression,attribution_window_seconds,numerator_definition," +
                    "denominator_definition,eligibility_definition,deduplication_definition" +
                    ") values(?,?,?,?,?,?,?,?,?,?,?) " +
                    "on conflict(metric_definition_version,metric_id) do nothing";

    private static final String INSERT_DATASET =
            "insert into public.recommendation_p2_dataset_snapshot(" +
                    "dataset_snapshot_id,dataset_schema_version,metric_definition_version," +
                    "experiment_id,experiment_version,observed_from,observed_to,observation_count," +
                    "canonicalization_version,canonical_payload,payload_size_bytes,content_hash" +
                    ") values(?,?,?,?,?,?,?,?,'canonical-json-v1',?,?,?) " +
                    "on conflict(dataset_snapshot_id) do nothing";

    private static final String INSERT_METRIC_RESULT =
            "insert into public.recommendation_p2_metric_result(" +
                    "evaluation_run_id,segment,metric_definition_version,metric_id," +
                    "baseline_count,treatment_count,eligible_exposed_count,missing_metric_count," +
                    "common_support_rate,baseline_mean,treatment_mean,raw_effect,oriented_effect," +
                    "effect_size,confidence_lower,confidence_upper,confidence_level,p_value," +
                    "adjusted_p_value,sample_sufficient,data_quality_pass,performance_pass" +
                    ") values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RecommendationP2EvaluationStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void store(
            EvaluationInput input,
            EvaluationResult result,
            RecommendationCanonicalPayload.Encoded canonicalPayload,
            String actorRef) {
        storeDefinitions(input.metricDefinitions());
        storeDataset(input, canonicalPayload);

        jdbc.update(
                INSERT_EVALUATION,
                input.evaluationRunId(),
                input.datasetSnapshotId(),
                result.metricDefinitionVersion(),
                input.evaluationPolicy().policyVersion(),
                input.experimentId(),
                input.experimentVersion(),
                input.baselinePolicyVersion(),
                input.treatmentPolicyVersion(),
                input.evaluatorBuildId(),
                Timestamp.from(input.evaluatedAt()),
                input.currentState().wireValue(),
                input.requestedState().wireValue(),
                input.operationalApproval(),
                result.finalDecision().wireValue(),
                result.targetState().wireValue(),
                result.fingerprint());

        for (SegmentResult segment : result.segments()) {
            for (MetricResult metric : segment.metrics()) {
                storeMetric(input.evaluationRunId(), result.metricDefinitionVersion(), metric);
            }
        }
        for (GateResult gate : result.gates()) {
            jdbc.update(
                    "insert into public.recommendation_p2_gate_result(" +
                            "evaluation_run_id,gate_id,gate_status,reason_codes" +
                            ") values(?,?,?,cast(? as jsonb))",
                    input.evaluationRunId(),
                    gate.gate().wireValue(),
                    gate.status().wireValue(),
                    toJson(gate.reasonCodes()));
        }

        // A release-decision row represents an actual state transition. A repeated HOLD result
        // remains append-only in evaluation evidence but does not create a fake transition.
        if (input.currentState() != result.targetState()) {
            jdbc.update(
                    "insert into public.recommendation_p2_release_decision(" +
                            "decision_id,evaluation_run_id,experiment_id,experiment_version," +
                            "from_state,to_state,final_decision,actor_ref,reason_code,decided_at" +
                            ") values(?,?,?,?,?,?,?,?,?,?)",
                    "p2-decision:" + UUID.randomUUID(),
                    input.evaluationRunId(),
                    input.experimentId(),
                    input.experimentVersion(),
                    input.currentState().wireValue(),
                    result.targetState().wireValue(),
                    result.finalDecision().wireValue(),
                    actorRef,
                    reasonCode(result),
                    Timestamp.from(input.evaluatedAt()));
        }
        verifyEvidence(input, result);
    }

    private void storeDefinitions(List<MetricDefinition> definitions) {
        for (MetricDefinition definition : definitions) {
            String numerator = definition.numeratorDefinition();
            String denominator = definition.denominatorDefinition();
            String eligibility = definition.eligibilityDefinition();
            String deduplication = definition.deduplicationDefinition();

            jdbc.update(
                    INSERT_METRIC_DEFINITION,
                    definition.metricDefinitionVersion(),
                    definition.metricId(),
                    definition.role().wireValue(),
                    definition.direction().wireValue(),
                    definition.minimumEffect(),
                    definition.maximumAllowedRegression(),
                    definition.attributionWindowSeconds(),
                    numerator,
                    denominator,
                    eligibility,
                    deduplication);

            Map<String, Object> stored = jdbc.queryForMap(
                    "select metric_role,direction,minimum_effect,maximum_allowed_regression," +
                            "attribution_window_seconds,numerator_definition,denominator_definition," +
                            "eligibility_definition,deduplication_definition " +
                            "from public.recommendation_p2_metric_definition " +
                            "where metric_definition_version=? and metric_id=?",
                    definition.metricDefinitionVersion(),
                    definition.metricId());

            boolean semanticsMatch =
                    Objects.equals(stored.get("metric_role"), definition.role().wireValue())
                    && Objects.equals(stored.get("direction"), definition.direction().wireValue())
                    && Double.doubleToRawLongBits(
                                    ((Number) stored.get("minimum_effect")).doubleValue())
                            == Double.doubleToRawLongBits(definition.minimumEffect())
                    && Double.doubleToRawLongBits(
                                    ((Number) stored.get("maximum_allowed_regression")).doubleValue())
                            == Double.doubleToRawLongBits(definition.maximumAllowedRegression())
                    && ((Number) stored.get("attribution_window_seconds")).longValue()
                            == definition.attributionWindowSeconds()
                    && Objects.equals(stored.get("numerator_definition"), numerator)
                    && Objects.equals(stored.get("denominator_definition"), denominator)
                    && Objects.equals(stored.get("eligibility_definition"), eligibility)
                    && Objects.equals(stored.get("deduplication_definition"), deduplication);
            if (!semanticsMatch) {
                throw new IllegalStateException(
                        "metric definition version is bound to different semantics");
            }
        }
    }

    private void storeDataset(
            EvaluationInput input,
            RecommendationCanonicalPayload.Encoded canonicalPayload) {
        byte[] bytes = canonicalPayload.bytes();
        String contentHash = RecommendationHashing.sha256(bytes);
        int inserted = jdbc.update(
                INSERT_DATASET,
                input.datasetSnapshotId(),
                input.datasetSchemaVersion(),
                input.metricDefinitions().getFirst().metricDefinitionVersion(),
                input.experimentId(),
                input.experimentVersion(),
                Timestamp.from(input.observedFrom()),
                Timestamp.from(input.observedTo()),
                input.observations().size(),
                bytes,
                bytes.length,
                contentHash);
        if (inserted == 0) {
            String storedHash = jdbc.queryForObject(
                    "select content_hash from public.recommendation_p2_dataset_snapshot " +
                            "where dataset_snapshot_id=?",
                    String.class,
                    input.datasetSnapshotId());
            if (!Objects.equals(storedHash, contentHash)) {
                throw new IllegalStateException("dataset snapshot conflict");
            }
        }
    }

    private void storeMetric(String evaluationRunId, String metricVersion, MetricResult metric) {
        jdbc.update(
                INSERT_METRIC_RESULT,
                evaluationRunId,
                metric.segment(),
                metricVersion,
                metric.metricId(),
                metric.baselineCount(),
                metric.treatmentCount(),
                metric.eligibleExposedCount(),
                metric.missingMetricCount(),
                metric.commonSupportRate(),
                metric.baselineMean(),
                metric.treatmentMean(),
                metric.rawEffect(),
                metric.orientedEffect(),
                metric.effectSize(),
                metric.confidenceInterval().lower(),
                metric.confidenceInterval().upper(),
                metric.confidenceInterval().confidenceLevel(),
                metric.pValue(),
                metric.adjustedPValue(),
                metric.sampleSufficient(),
                metric.dataQualityPass(),
                metric.performancePass());
    }

    private void verifyEvidence(EvaluationInput input, EvaluationResult result) {
        String storedFingerprint = jdbc.queryForObject(
                "select evaluation_fingerprint from public.recommendation_p2_evaluation_run " +
                        "where evaluation_run_id=?",
                String.class,
                input.evaluationRunId());
        if (!Objects.equals(storedFingerprint, result.fingerprint())) {
            throw new IllegalStateException("evaluation fingerprint mismatch");
        }

        Integer gateCount = jdbc.queryForObject(
                "select count(*)::integer from public.recommendation_p2_gate_result " +
                        "where evaluation_run_id=?",
                Integer.class,
                input.evaluationRunId());
        Integer decisionCount = jdbc.queryForObject(
                "select count(*)::integer from public.recommendation_p2_release_decision " +
                        "where evaluation_run_id=?",
                Integer.class,
                input.evaluationRunId());
        int expectedDecisionCount = input.currentState() == result.targetState() ? 0 : 1;
        if (gateCount == null
                || gateCount != result.gates().size()
                || decisionCount == null
                || decisionCount != expectedDecisionCount) {
            throw new IllegalStateException("P2 evidence incomplete");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("P2 evidence JSON invalid", exception);
        }
    }

    private static String reasonCode(EvaluationResult result) {
        return switch (result.finalDecision()) {
            case CANARY, LIVE -> "ALL_GATES_PASS";
            case HOLD -> "EVALUATION_HOLD";
            case ROLLBACK -> "GUARDRAIL_ROLLBACK";
        };
    }
}
