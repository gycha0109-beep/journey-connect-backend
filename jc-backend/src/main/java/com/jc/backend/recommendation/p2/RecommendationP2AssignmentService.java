package com.jc.backend.recommendation.p2;

import com.jc.backend.recommendation.config.RecommendationP2Properties;
import com.jc.recommendation.p2.P2ExperimentAssigner;
import com.jc.recommendation.p2.P2ExperimentAssigner.Assignment;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class RecommendationP2AssignmentService {

    private final RecommendationP2Properties properties;
    private final RecommendationP2AssignmentStore store;
    private final P2ExperimentAssigner assigner = new P2ExperimentAssigner();

    public RecommendationP2AssignmentService(
            RecommendationP2Properties properties,
            RecommendationP2AssignmentStore store) {
        this.properties = properties;
        this.store = store;
    }

    public boolean isEnabled() {
        return properties.isAssignmentEnabled();
    }

    public Assignment assign(long userId, Instant assignedAt) {
        properties.validate();
        Assignment assignment = assigner.assign(
                properties.getExperimentId(),
                properties.getExperimentVersion(),
                "user:" + userId,
                properties.getTreatmentAllocationBasisPoints(),
                properties.getAssignmentSalt());
        return store.store(
                assignment,
                userId,
                assignedAt,
                properties.getAssignmentBuildId());
    }

    public String recordExposure(
            Assignment assignment,
            String runId,
            long userId,
            String sessionId,
            Instant exposedAt) {
        return store.storeExposure(assignment, runId, userId, sessionId, exposedAt);
    }
}
