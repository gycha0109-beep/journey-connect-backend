package com.jc.data.contract.v1.projection;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record ProjectionDefinition(
        String projectionName,
        String projectionSchemaVersion,
        String projectionPolicyVersion,
        String featurePolicyVersion,
        String identityBindingVersion,
        String targetContractVersion,
        List<Integer> activityWindowsDays,
        Duration outcomeWindow,
        int maxRankedReferences) {

    public static final String PROFILE_NAME = "recommendation-profile-input-v1";
    public static final String OUTCOME_NAME = "experiment-outcome-input-v1";

    public ProjectionDefinition {
        projectionName = ProjectionEngineSupport.requireToken(projectionName, "projectionName", 96);
        projectionSchemaVersion = ProjectionEngineSupport.requireVersion(
                projectionSchemaVersion, "projectionSchemaVersion");
        projectionPolicyVersion = ProjectionEngineSupport.requireVersion(
                projectionPolicyVersion, "projectionPolicyVersion");
        featurePolicyVersion = ProjectionEngineSupport.requireVersion(featurePolicyVersion, "featurePolicyVersion");
        identityBindingVersion = ProjectionEngineSupport.requireVersion(
                identityBindingVersion, "identityBindingVersion");
        targetContractVersion = ProjectionEngineSupport.requireVersion(
                targetContractVersion, "targetContractVersion");
        activityWindowsDays = List.copyOf(Objects.requireNonNull(activityWindowsDays, "activityWindowsDays"));
        Objects.requireNonNull(outcomeWindow, "outcomeWindow");
        if (!List.of(PROFILE_NAME, OUTCOME_NAME).contains(projectionName)) {
            throw new IllegalArgumentException("unsupported projectionName");
        }
        if (PROFILE_NAME.equals(projectionName)) {
            if (!activityWindowsDays.equals(List.of(7, 30, 90))) {
                throw new IllegalArgumentException("profile windows must be 7, 30 and 90 days");
            }
        } else if (!activityWindowsDays.isEmpty()) {
            throw new IllegalArgumentException("outcome projection cannot define profile windows");
        }
        if (!Duration.ofDays(7).equals(outcomeWindow)) {
            throw new IllegalArgumentException("outcomeWindow must be seven days");
        }
        if (maxRankedReferences < 1 || maxRankedReferences > 100) {
            throw new IllegalArgumentException("maxRankedReferences out of range");
        }
    }

    public static ProjectionDefinition profileV1(String identityBindingVersion) {
        return new ProjectionDefinition(
                PROFILE_NAME,
                "recommendation-profile-input-v1",
                "recommendation-profile-projection-policy-v1",
                "recommendation-profile-feature-policy-v1",
                identityBindingVersion,
                PROFILE_NAME,
                List.of(7, 30, 90),
                Duration.ofDays(7),
                20);
    }

    public static ProjectionDefinition outcomeV1(String identityBindingVersion) {
        return new ProjectionDefinition(
                OUTCOME_NAME,
                "experiment-outcome-input-v1",
                "experiment-outcome-projection-policy-v1",
                "experiment-outcome-feature-policy-v1",
                identityBindingVersion,
                OUTCOME_NAME,
                List.of(),
                Duration.ofDays(7),
                100);
    }
}
