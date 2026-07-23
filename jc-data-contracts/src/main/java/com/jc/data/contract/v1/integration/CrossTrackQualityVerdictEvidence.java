package com.jc.data.contract.v1.integration;

public record CrossTrackQualityVerdictEvidence(
        String verdictRef,
        String snapshotRef,
        String qualityPolicyVersion,
        CrossTrackQualityVerdictStatus overallStatus,
        String verdictFingerprint,
        boolean conflicted,
        boolean authoritative) {
    public CrossTrackQualityVerdictEvidence {
        verdictRef = IntegrationSupport.reference(verdictRef, "verdictRef");
        snapshotRef = IntegrationSupport.reference(snapshotRef, "snapshotRef");
        qualityPolicyVersion = IntegrationSupport.version(qualityPolicyVersion, "qualityPolicyVersion");
        if (overallStatus == null) throw new NullPointerException("overallStatus");
        verdictFingerprint = IntegrationSupport.fingerprint(verdictFingerprint, "verdictFingerprint");
    }
}
