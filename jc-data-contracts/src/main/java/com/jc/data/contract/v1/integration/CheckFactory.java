package com.jc.data.contract.v1.integration;

final class CheckFactory {
    private CheckFactory() { }
    static CrossTrackIntegrationCheck check(int order, String code, CrossTrackIntegrationScope scope,
            String source, String target, String expected, String observed, boolean success,
            CrossTrackIntegrationFailure failure, boolean required, boolean conditional) {
        CrossTrackIntegrationCheckStatus status = success ? CrossTrackIntegrationCheckStatus.PASS
                : CrossTrackIntegrationCheckStatus.FAIL;
        CrossTrackIntegrationSeverity severity = success ? (conditional ? CrossTrackIntegrationSeverity.WARNING
                : CrossTrackIntegrationSeverity.INFO) : CrossTrackIntegrationSeverity.BLOCKER;
        String fingerprint = CrossTrackFingerprints.check(order, code, scope, source, target, expected, observed,
                severity, status, success ? null : failure, required, conditional);
        return new CrossTrackIntegrationCheck(order, code, scope, source, target, expected, observed, severity,
                status, success ? null : failure, required, conditional, fingerprint);
    }
    static CrossTrackIntegrationCheck skipped(int order, String code, CrossTrackIntegrationScope scope,
            String source, String target, CrossTrackIntegrationFailure failure, boolean required) {
        CrossTrackIntegrationSeverity severity = required ? CrossTrackIntegrationSeverity.ERROR : CrossTrackIntegrationSeverity.WARNING;
        CrossTrackIntegrationCheckStatus status = CrossTrackIntegrationCheckStatus.SKIPPED;
        String fingerprint = CrossTrackFingerprints.check(order, code, scope, source, target, "verified", "not_executed",
                severity, status, failure, required, false);
        return new CrossTrackIntegrationCheck(order, code, scope, source, target, "verified", "not_executed",
                severity, status, failure, required, false, fingerprint);
    }
}
