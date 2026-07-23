package com.jc.data.contract.v1.quality;

import java.util.Objects;

public record DataQualityCheckResult(
        String checkCode,
        DataQualityValidationScope checkScope,
        String expectedValue,
        String observedValue,
        String differenceValue,
        DataQualitySeverity severity,
        DataQualityCheckStatus checkStatus,
        DataQualityFailure failureCode,
        String reasonCode,
        boolean required,
        String evidenceFingerprint) {
    public DataQualityCheckResult {
        checkCode = QualityContractSupport.token(checkCode, "checkCode", 96);
        Objects.requireNonNull(checkScope, "checkScope");
        expectedValue = Objects.requireNonNull(expectedValue, "expectedValue");
        observedValue = Objects.requireNonNull(observedValue, "observedValue");
        differenceValue = Objects.requireNonNull(differenceValue, "differenceValue");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(checkStatus, "checkStatus");
        if (checkStatus == DataQualityCheckStatus.FAIL && failureCode == null) {
            throw new IllegalArgumentException("failed check requires failureCode");
        }
        if (checkStatus != DataQualityCheckStatus.FAIL && failureCode != null) {
            throw new IllegalArgumentException("non-failed check cannot carry failureCode");
        }
        if ((checkStatus == DataQualityCheckStatus.SKIPPED || checkStatus == DataQualityCheckStatus.NOT_APPLICABLE)
                && (reasonCode == null || reasonCode.isBlank())) {
            throw new IllegalArgumentException("skipped/not-applicable check requires reasonCode");
        }
        evidenceFingerprint = QualityContractSupport.fingerprint(evidenceFingerprint, "evidenceFingerprint");
    }
}
