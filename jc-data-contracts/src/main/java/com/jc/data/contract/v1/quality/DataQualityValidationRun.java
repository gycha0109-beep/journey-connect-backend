package com.jc.data.contract.v1.quality;

import java.time.Instant;
import java.util.Objects;

public record DataQualityValidationRun(
        String validationRunRef,
        DataQualityValidationDefinition definition,
        String validationInputFingerprint,
        Instant createdAt,
        String retentionClass,
        String retentionPolicyVersion,
        Instant expiresAt) {
    public DataQualityValidationRun {
        validationRunRef = QualityContractSupport.reference(validationRunRef, "validationRunRef");
        Objects.requireNonNull(definition, "definition");
        validationInputFingerprint = QualityContractSupport.fingerprint(validationInputFingerprint, "validationInputFingerprint");
        Objects.requireNonNull(createdAt, "createdAt");
        retentionClass = QualityContractSupport.token(retentionClass, "retentionClass", 40);
        retentionPolicyVersion = QualityContractSupport.version(retentionPolicyVersion, "retentionPolicyVersion");
        expiresAt = QualityContractSupport.retention(createdAt, expiresAt);
    }
}
