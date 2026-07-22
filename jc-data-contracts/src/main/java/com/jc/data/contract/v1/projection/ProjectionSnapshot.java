package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.Objects;

public record ProjectionSnapshot(
        String snapshotRef,
        String projectionRunRef,
        String projectionName,
        String projectionSchemaVersion,
        String projectionPolicyVersion,
        String sourceCheckpointRef,
        Instant snapshotAsOf,
        long recordCount,
        long subjectCount,
        long sourceEventCount,
        String contentFingerprint,
        String lineageFingerprint,
        ProjectionSnapshotStatus snapshotStatus,
        Instant createdAt,
        String retentionClass,
        String retentionPolicyVersion,
        Instant expiresAt) {

    public ProjectionSnapshot {
        snapshotRef = ProjectionEngineSupport.requireReference(snapshotRef, "snapshotRef");
        projectionRunRef = ProjectionEngineSupport.requireReference(projectionRunRef, "projectionRunRef");
        projectionName = ProjectionEngineSupport.requireToken(projectionName, "projectionName", 96);
        projectionSchemaVersion = ProjectionEngineSupport.requireVersion(
                projectionSchemaVersion, "projectionSchemaVersion");
        projectionPolicyVersion = ProjectionEngineSupport.requireVersion(
                projectionPolicyVersion, "projectionPolicyVersion");
        sourceCheckpointRef = ProjectionEngineSupport.requireReference(
                sourceCheckpointRef, "sourceCheckpointRef");
        Objects.requireNonNull(snapshotAsOf, "snapshotAsOf");
        if (recordCount < 1 || subjectCount < 1 || sourceEventCount < 0) {
            throw new IllegalArgumentException("snapshot counts are invalid");
        }
        contentFingerprint = ProjectionEngineSupport.requireFingerprint(contentFingerprint, "contentFingerprint");
        lineageFingerprint = ProjectionEngineSupport.requireFingerprint(lineageFingerprint, "lineageFingerprint");
        Objects.requireNonNull(snapshotStatus, "snapshotStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        retentionClass = ProjectionEngineSupport.requireToken(retentionClass, "retentionClass", 40);
        retentionPolicyVersion = ProjectionEngineSupport.requireVersion(
                retentionPolicyVersion, "retentionPolicyVersion");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (expiresAt.isBefore(createdAt.plusSeconds(7_776_000L))) {
            throw new IllegalArgumentException("snapshot retention must be at least 90 days");
        }
    }
}
