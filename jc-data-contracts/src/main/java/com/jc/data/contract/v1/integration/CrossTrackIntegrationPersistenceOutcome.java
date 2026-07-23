package com.jc.data.contract.v1.integration;

public record CrossTrackIntegrationPersistenceOutcome(
        CrossTrackIntegrationPersistenceDisposition disposition,
        String integrationRunRef,
        String verdictFingerprint,
        CrossTrackIntegrationFailure failure) {
    public CrossTrackIntegrationPersistenceOutcome {
        if (disposition == null) throw new NullPointerException("disposition");
        integrationRunRef = IntegrationSupport.reference(integrationRunRef, "integrationRunRef");
        verdictFingerprint = IntegrationSupport.fingerprint(verdictFingerprint, "verdictFingerprint");
        if (disposition == CrossTrackIntegrationPersistenceDisposition.CONFLICT
                && failure != CrossTrackIntegrationFailure.CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT) {
            throw new IllegalArgumentException("conflict outcome requires stable conflict failure");
        }
    }
}
