package com.jc.data.contract.v1.quality;

import java.util.Objects;

public record DataQualityAnomaly(
        DataQualityValidationScope scope,
        DataQualityFailure failureCode,
        DataQualitySeverity severity,
        String evidenceReference,
        String evidenceFingerprint) {
    public DataQualityAnomaly {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(severity, "severity");
        evidenceReference = QualityContractSupport.reference(evidenceReference, "evidenceReference");
        evidenceFingerprint = QualityContractSupport.fingerprint(evidenceFingerprint, "evidenceFingerprint");
    }
}
