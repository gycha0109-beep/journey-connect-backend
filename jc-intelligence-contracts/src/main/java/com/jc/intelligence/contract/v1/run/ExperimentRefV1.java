package com.jc.intelligence.contract.v1.run;

import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.version.SchemaVersion;

public record ExperimentRefV1(
        String experimentId,
        SchemaVersion experimentVersion,
        String assignmentId) {
    public ExperimentRefV1 {
        experimentId = ContractChecks.requireText(
                experimentId,
                "experimentId",
                IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        java.util.Objects.requireNonNull(experimentVersion, "experimentVersion");
        if (assignmentId != null) {
            assignmentId = ContractChecks.requireText(
                    assignmentId,
                    "assignmentId",
                    IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        }
    }
}
