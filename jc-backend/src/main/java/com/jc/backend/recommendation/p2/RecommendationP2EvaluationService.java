package com.jc.backend.recommendation.p2;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import com.jc.backend.recommendation.config.RecommendationP2Properties;
import com.jc.recommendation.p2.P2EvaluationContracts.EvaluationInput;
import com.jc.recommendation.p2.P2EvaluationContracts.EvaluationResult;
import com.jc.recommendation.p2.P2EvaluationContracts.Observation;
import com.jc.recommendation.p2.P2EvaluationContracts.ReleaseState;
import com.jc.recommendation.p2.P2EvaluationEngine;
import com.jc.recommendation.p2.P2Policies;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RecommendationP2EvaluationService {

    private final RecommendationP2Properties properties;
    private final RecommendationP2ObservationSource observationSource;
    private final RecommendationP2EvaluationStore evaluationStore;
    private final RecommendationCanonicalPayload canonicalPayload;
    private final P2EvaluationEngine evaluationEngine = new P2EvaluationEngine();

    public RecommendationP2EvaluationService(
            RecommendationP2Properties properties,
            RecommendationP2ObservationSource observationSource,
            RecommendationP2EvaluationStore evaluationStore,
            RecommendationCanonicalPayload canonicalPayload) {
        this.properties = properties;
        this.observationSource = observationSource;
        this.evaluationStore = evaluationStore;
        this.canonicalPayload = canonicalPayload;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public EvaluationResult evaluateRuntime(EvaluationRequest request) {
        properties.validate();
        List<Observation> observations = observationSource.findObservations(
                properties.getExperimentId(),
                properties.getExperimentVersion(),
                request.observedFrom(),
                request.observedTo());
        EvaluationInput input = new EvaluationInput(
                request.evaluationRunId(),
                properties.getExperimentId(),
                properties.getExperimentVersion(),
                request.datasetSnapshotId(),
                P2Policies.DATASET_SCHEMA_VERSION,
                request.baselinePolicyVersion(),
                request.treatmentPolicyVersion(),
                request.observedFrom(),
                request.observedTo(),
                request.evaluatedAt(),
                P2Policies.METRICS_V1,
                observations,
                P2Policies.EVALUATION_POLICY_V1,
                request.currentState(),
                request.requestedState(),
                request.operationalApproval(),
                properties.getEvaluatorBuildId());
        return evaluateAndStore(input, request.actorRef());
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public EvaluationResult evaluateAndStore(EvaluationInput input, String actorRef) {
        if (actorRef == null || !actorRef.matches("(user|system):[A-Za-z0-9._:-]{1,121}")) {
            throw new IllegalArgumentException("actorRef invalid");
        }

        EvaluationResult result = evaluationEngine.evaluate(input);
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("datasetSchemaVersion", input.datasetSchemaVersion());
        dataset.put("metricDefinitions", input.metricDefinitions());
        dataset.put("experimentId", input.experimentId());
        dataset.put("experimentVersion", input.experimentVersion());
        dataset.put("observedFrom", input.observedFrom());
        dataset.put("observedTo", input.observedTo());
        dataset.put("observations", input.observations());
        RecommendationCanonicalPayload.Encoded encoded = canonicalPayload.encode(dataset);
        evaluationStore.store(input, result, encoded, actorRef);
        return result;
    }

    public record EvaluationRequest(
            String evaluationRunId,
            String datasetSnapshotId,
            String baselinePolicyVersion,
            String treatmentPolicyVersion,
            Instant observedFrom,
            Instant observedTo,
            Instant evaluatedAt,
            ReleaseState currentState,
            ReleaseState requestedState,
            boolean operationalApproval,
            String actorRef) {}
}
