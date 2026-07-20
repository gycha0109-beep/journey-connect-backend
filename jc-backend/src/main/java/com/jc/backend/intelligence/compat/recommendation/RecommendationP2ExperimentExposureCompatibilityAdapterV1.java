package com.jc.backend.intelligence.compat.recommendation;

import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import java.time.Instant;

public final class RecommendationP2ExperimentExposureCompatibilityAdapterV1 {
    public RecommendationP2ExperimentExposureCompatibilityViewV1 adapt(
            RecommendationP2ExperimentExposureCompatibilityInputV1 source) {
        java.util.Objects.requireNonNull(source, "source");
        return new RecommendationP2ExperimentExposureCompatibilityViewV1(
                source.exposureId(),
                source.assignmentId(),
                new RunRef(source.runId()),
                SubjectRef.legacyUser(source.userId()),
                source.sessionId(),
                source.variant(),
                source.exposedAt(),
                source.exposureFingerprint(),
                ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1,
                true,
                false);
    }

    public record RecommendationP2ExperimentExposureCompatibilityInputV1(
            String exposureId,
            String assignmentId,
            String runId,
            long userId,
            String sessionId,
            String variant,
            Instant exposedAt,
            String exposureFingerprint) {
        public RecommendationP2ExperimentExposureCompatibilityInputV1 {
            java.util.Objects.requireNonNull(exposureId, "exposureId");
            java.util.Objects.requireNonNull(assignmentId, "assignmentId");
            java.util.Objects.requireNonNull(runId, "runId");
            if (userId <= 0L) {
                throw new IllegalArgumentException("userId must be positive");
            }
            java.util.Objects.requireNonNull(sessionId, "sessionId");
            if (!java.util.Set.of("baseline", "treatment").contains(variant)) {
                throw new IllegalArgumentException("variant must be baseline or treatment");
            }
            java.util.Objects.requireNonNull(exposedAt, "exposedAt");
            if (!exposureFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("exposureFingerprint must be lowercase SHA-256");
            }
        }
    }

    public record RecommendationP2ExperimentExposureCompatibilityViewV1(
            String exposureId,
            String assignmentId,
            RunRef runId,
            SubjectRef subjectRef,
            String sessionId,
            String variant,
            Instant exposedAt,
            String exposureFingerprint,
            ExposureSourceId exposureSourceId,
            boolean authoritativeP2ExperimentDenominator,
            boolean mergedWithGeneralOrBehaviorExposure) {
        public RecommendationP2ExperimentExposureCompatibilityViewV1 {
            if (exposureSourceId != ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1
                    || !authoritativeP2ExperimentDenominator
                    || mergedWithGeneralOrBehaviorExposure) {
                throw new IllegalArgumentException("P2 exposure authority must remain isolated");
            }
        }
    }
}
