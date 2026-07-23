package com.jc.data.contract.v1.integration;

public record CrossTrackIntegrationVerdict(
        CrossTrackIntegrationVerdictStatus overallStatus,
        long blockerCount,
        long errorCount,
        long warningCount,
        long passedCheckCount,
        long failedCheckCount,
        long skippedRequiredCheckCount,
        long conditionalRequirementCount,
        String verdictFingerprint) {
    public CrossTrackIntegrationVerdict {
        if (overallStatus == null) throw new NullPointerException("overallStatus");
        if (blockerCount < 0 || errorCount < 0 || warningCount < 0 || passedCheckCount < 0
                || failedCheckCount < 0 || skippedRequiredCheckCount < 0 || conditionalRequirementCount < 0) {
            throw new IllegalArgumentException("verdict counts cannot be negative");
        }
        verdictFingerprint = IntegrationSupport.fingerprint(verdictFingerprint, "verdictFingerprint");
    }
}
