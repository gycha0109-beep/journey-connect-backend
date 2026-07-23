package com.jc.data.contract.v1.integration;

public record CrossTrackIntegrationCheck(
        int order,
        String checkCode,
        CrossTrackIntegrationScope checkScope,
        String sourceReference,
        String targetReference,
        String expectedValue,
        String observedValue,
        CrossTrackIntegrationSeverity severity,
        CrossTrackIntegrationCheckStatus checkStatus,
        CrossTrackIntegrationFailure failure,
        boolean required,
        boolean conditionalRequirement,
        String evidenceFingerprint) {
    public CrossTrackIntegrationCheck {
        if (order < 0) throw new IllegalArgumentException("order cannot be negative");
        checkCode = IntegrationSupport.text(checkCode, "checkCode");
        if (checkScope == null) throw new NullPointerException("checkScope");
        sourceReference = sourceReference == null ? "" : sourceReference;
        targetReference = targetReference == null ? "" : targetReference;
        expectedValue = expectedValue == null ? "" : expectedValue;
        observedValue = observedValue == null ? "" : observedValue;
        if (severity == null) throw new NullPointerException("severity");
        if (checkStatus == null) throw new NullPointerException("checkStatus");
        if (checkStatus == CrossTrackIntegrationCheckStatus.FAIL && failure == null) {
            throw new IllegalArgumentException("failed check requires failure");
        }
        if (checkStatus != CrossTrackIntegrationCheckStatus.FAIL && failure != null
                && checkStatus != CrossTrackIntegrationCheckStatus.SKIPPED) {
            throw new IllegalArgumentException("non-failed check cannot carry failure");
        }
        evidenceFingerprint = IntegrationSupport.fingerprint(evidenceFingerprint, "evidenceFingerprint");
    }
}
