package com.jc.data.contract.v1.integration;

import java.time.Instant;

public record CrossTrackIntegrationRun(
        String integrationRunRef,
        CrossTrackIntegrationDefinition definition,
        String sourceSnapshotRef,
        String sourceQualityVerdictRef,
        Instant validationAsOf,
        Instant createdAt,
        String retentionClass,
        String retentionPolicyVersion,
        Instant expiresAt,
        String logicalIdentityFingerprint,
        String integrationInputFingerprint) {
    public CrossTrackIntegrationRun {
        integrationRunRef = IntegrationSupport.reference(integrationRunRef, "integrationRunRef");
        if (definition == null) throw new NullPointerException("definition");
        sourceSnapshotRef = IntegrationSupport.reference(sourceSnapshotRef, "sourceSnapshotRef");
        sourceQualityVerdictRef = IntegrationSupport.reference(sourceQualityVerdictRef, "sourceQualityVerdictRef");
        validationAsOf = IntegrationSupport.instant(validationAsOf, "validationAsOf");
        createdAt = IntegrationSupport.instant(createdAt, "createdAt");
        retentionClass = IntegrationSupport.text(retentionClass, "retentionClass");
        retentionPolicyVersion = IntegrationSupport.version(retentionPolicyVersion, "retentionPolicyVersion");
        expiresAt = IntegrationSupport.instant(expiresAt, "expiresAt");
        if (expiresAt.isBefore(createdAt.plusSeconds(90L * 24L * 60L * 60L))) {
            throw new IllegalArgumentException("expiresAt must retain integration evidence for at least 90 days");
        }
        logicalIdentityFingerprint = IntegrationSupport.fingerprint(logicalIdentityFingerprint, "logicalIdentityFingerprint");
        integrationInputFingerprint = IntegrationSupport.fingerprint(integrationInputFingerprint, "integrationInputFingerprint");
    }
}
