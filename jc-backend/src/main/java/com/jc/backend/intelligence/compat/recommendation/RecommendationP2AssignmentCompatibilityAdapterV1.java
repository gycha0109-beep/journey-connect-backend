package com.jc.backend.intelligence.compat.recommendation;

import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.run.ExperimentRefV1;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;

public final class RecommendationP2AssignmentCompatibilityAdapterV1 {
    public RecommendationP2AssignmentCompatibilityViewV1 adapt(
            RecommendationP2AssignmentCompatibilityInputV1 source) {
        java.util.Objects.requireNonNull(source, "source");
        SubjectRef subjectRef = new SubjectRef(
                com.jc.intelligence.contract.v1.identity.IdentitySchemeId.LEGACY_USER_NUMERIC_V1,
                source.subjectRef());
        return new RecommendationP2AssignmentCompatibilityViewV1(
                new ExperimentRefV1(
                        source.experimentId(),
                        new SchemaVersion(source.experimentVersion()),
                        source.assignmentId()),
                subjectRef,
                source.variant(),
                source.assignedAt(),
                "recommendation_p2_experiment_assignment",
                "reliability",
                "recommendation_p2",
                false);
    }

    public record RecommendationP2AssignmentCompatibilityInputV1(
            String assignmentId,
            String experimentId,
            String experimentVersion,
            String subjectRef,
            String variant,
            Instant assignedAt) {
        public RecommendationP2AssignmentCompatibilityInputV1 {
            java.util.Objects.requireNonNull(assignmentId, "assignmentId");
            java.util.Objects.requireNonNull(experimentId, "experimentId");
            java.util.Objects.requireNonNull(experimentVersion, "experimentVersion");
            java.util.Objects.requireNonNull(subjectRef, "subjectRef");
            if (!java.util.Set.of("baseline", "treatment").contains(variant)) {
                throw new IllegalArgumentException("variant must be baseline or treatment");
            }
            java.util.Objects.requireNonNull(assignedAt, "assignedAt");
        }
    }

    public record RecommendationP2AssignmentCompatibilityViewV1(
            ExperimentRefV1 experimentRef,
            SubjectRef subjectRef,
            String variant,
            Instant assignedAt,
            String authoritativeSource,
            String semanticOwner,
            String currentPhysicalWriter,
            boolean identityAutomaticallyMapped) {
        public RecommendationP2AssignmentCompatibilityViewV1 {
            if (identityAutomaticallyMapped) {
                throw new IllegalArgumentException("IP-1 must not implement identity mapping");
            }
        }
    }
}
