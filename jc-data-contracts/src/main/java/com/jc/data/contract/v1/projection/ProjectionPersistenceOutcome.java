package com.jc.data.contract.v1.projection;

import java.util.Objects;

public record ProjectionPersistenceOutcome(
        ProjectionPersistenceDisposition disposition,
        String snapshotRef,
        String errorCode) {

    public ProjectionPersistenceOutcome {
        Objects.requireNonNull(disposition, "disposition");
        if (snapshotRef != null) {
            snapshotRef = ProjectionEngineSupport.requireReference(snapshotRef, "snapshotRef");
        }
        if (errorCode != null) {
            errorCode = ProjectionEngineSupport.requireToken(errorCode, "errorCode", 96);
        }
        if (disposition == ProjectionPersistenceDisposition.CONFLICT
                && !"PROJECTION_SNAPSHOT_CONFLICT".equals(errorCode)) {
            throw new IllegalArgumentException("conflict requires PROJECTION_SNAPSHOT_CONFLICT");
        }
        if (disposition != ProjectionPersistenceDisposition.CONFLICT && errorCode != null) {
            throw new IllegalArgumentException("non-conflict persistence outcome cannot carry errorCode");
        }
    }

    public static ProjectionPersistenceOutcome decide(
            String existingSnapshotRef,
            String existingFingerprint,
            String proposedFingerprint) {
        ProjectionEngineSupport.requireFingerprint(proposedFingerprint, "proposedFingerprint");
        if (existingSnapshotRef == null && existingFingerprint == null) {
            return new ProjectionPersistenceOutcome(ProjectionPersistenceDisposition.NEW, null, null);
        }
        Objects.requireNonNull(existingSnapshotRef, "existingSnapshotRef");
        ProjectionEngineSupport.requireFingerprint(existingFingerprint, "existingFingerprint");
        if (existingFingerprint.equals(proposedFingerprint)) {
            return new ProjectionPersistenceOutcome(
                    ProjectionPersistenceDisposition.DUPLICATE, existingSnapshotRef, null);
        }
        return new ProjectionPersistenceOutcome(
                ProjectionPersistenceDisposition.CONFLICT,
                existingSnapshotRef,
                "PROJECTION_SNAPSHOT_CONFLICT");
    }
}
