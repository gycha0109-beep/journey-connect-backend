package com.jc.data.contract.v1.quality;

import java.time.Instant;
import java.util.Objects;

public record DataQualityValidationDefinition(
        DataQualityValidationScope validationScope,
        String snapshotRef,
        String projectionName,
        String projectionSchemaVersion,
        String projectionPolicyVersion,
        String sourceCheckpointRef,
        String validatorVersion,
        String qualityPolicyVersion,
        Instant validationAsOf) {
    public DataQualityValidationDefinition {
        Objects.requireNonNull(validationScope, "validationScope");
        snapshotRef = QualityContractSupport.reference(snapshotRef, "snapshotRef");
        projectionName = QualityContractSupport.token(projectionName, "projectionName", 96);
        projectionSchemaVersion = QualityContractSupport.version(projectionSchemaVersion, "projectionSchemaVersion");
        projectionPolicyVersion = QualityContractSupport.version(projectionPolicyVersion, "projectionPolicyVersion");
        sourceCheckpointRef = QualityContractSupport.reference(sourceCheckpointRef, "sourceCheckpointRef");
        validatorVersion = QualityContractSupport.version(validatorVersion, "validatorVersion");
        qualityPolicyVersion = QualityContractSupport.version(qualityPolicyVersion, "qualityPolicyVersion");
        Objects.requireNonNull(validationAsOf, "validationAsOf");
    }
}
