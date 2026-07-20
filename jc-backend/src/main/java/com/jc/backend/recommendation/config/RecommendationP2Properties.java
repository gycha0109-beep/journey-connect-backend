package com.jc.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recommendation.p2")
public class RecommendationP2Properties {

    private boolean assignmentEnabled;
    private String experimentId = "recommendation-p1";
    private String experimentVersion = "experiment-v1";
    private String assignmentSalt = "recommendation-assignment-salt-v1";
    private int treatmentAllocationBasisPoints;
    private String assignmentBuildId = "p2-assignment-build-v1";
    private String evaluatorBuildId = "p2-evaluator-build-v1";

    public boolean isAssignmentEnabled() {
        return assignmentEnabled;
    }

    public void setAssignmentEnabled(boolean assignmentEnabled) {
        this.assignmentEnabled = assignmentEnabled;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public String getExperimentVersion() {
        return experimentVersion;
    }

    public void setExperimentVersion(String experimentVersion) {
        this.experimentVersion = experimentVersion;
    }

    public String getAssignmentSalt() {
        return assignmentSalt;
    }

    public void setAssignmentSalt(String assignmentSalt) {
        this.assignmentSalt = assignmentSalt;
    }

    public int getTreatmentAllocationBasisPoints() {
        return treatmentAllocationBasisPoints;
    }

    public void setTreatmentAllocationBasisPoints(int treatmentAllocationBasisPoints) {
        this.treatmentAllocationBasisPoints = treatmentAllocationBasisPoints;
    }

    public String getAssignmentBuildId() {
        return assignmentBuildId;
    }

    public void setAssignmentBuildId(String assignmentBuildId) {
        this.assignmentBuildId = assignmentBuildId;
    }

    public String getEvaluatorBuildId() {
        return evaluatorBuildId;
    }

    public void setEvaluatorBuildId(String evaluatorBuildId) {
        this.evaluatorBuildId = evaluatorBuildId;
    }

    public void validate() {
        requireIdentifier(experimentId, "experiment-id");
        requireIdentifier(experimentVersion, "experiment-version");
        requireIdentifier(assignmentSalt, "assignment-salt");
        requireIdentifier(assignmentBuildId, "assignment-build-id");
        requireIdentifier(evaluatorBuildId, "evaluator-build-id");
        if (treatmentAllocationBasisPoints < 0 || treatmentAllocationBasisPoints > 10_000) {
            throw new IllegalStateException("treatment allocation must be 0..10000");
        }
        if (assignmentEnabled && treatmentAllocationBasisPoints == 0) {
            throw new IllegalStateException(
                    "enabled P2 assignment requires nonzero treatment allocation");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalStateException("app.recommendation.p2." + field + " is invalid");
        }
    }
}
