package com.jc.data.contract.v1.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CrossTrackSourceSnapshot(
        String snapshotRef,
        String projectionName,
        String sourceContract,
        String sourceSchemaVersion,
        String projectionPolicyVersion,
        String contentFingerprint,
        String lineageFingerprint,
        String identityNamespace,
        String producerBuildId,
        Instant snapshotAsOf,
        int sourceRetentionDays,
        List<String> sourceEventRefs,
        Map<String, Object> fields) {
    public CrossTrackSourceSnapshot {
        snapshotRef = IntegrationSupport.reference(snapshotRef, "snapshotRef");
        projectionName = IntegrationSupport.version(projectionName, "projectionName");
        sourceContract = IntegrationSupport.version(sourceContract, "sourceContract");
        sourceSchemaVersion = IntegrationSupport.version(sourceSchemaVersion, "sourceSchemaVersion");
        projectionPolicyVersion = IntegrationSupport.version(projectionPolicyVersion, "projectionPolicyVersion");
        contentFingerprint = IntegrationSupport.fingerprint(contentFingerprint, "contentFingerprint");
        lineageFingerprint = IntegrationSupport.fingerprint(lineageFingerprint, "lineageFingerprint");
        identityNamespace = IntegrationSupport.reference(identityNamespace, "identityNamespace");
        if (producerBuildId == null || !producerBuildId.matches("git:[0-9a-f]{40}")) {
            throw new IllegalArgumentException("producerBuildId");
        }
        snapshotAsOf = IntegrationSupport.instant(snapshotAsOf, "snapshotAsOf");
        if (sourceRetentionDays < 1) throw new IllegalArgumentException("sourceRetentionDays");
        sourceEventRefs = IntegrationSupport.list(sourceEventRefs, "sourceEventRefs");
        fields = IntegrationSupport.map(fields, "fields");
    }
}
