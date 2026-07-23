package com.jc.data.contract.v1.quality;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record LateArrivalObservation(
        String sourceEventRef,
        String affectedCheckpointRef,
        String affectedSnapshotRef,
        Instant eventTime,
        Instant ingestedAt,
        Duration latenessDuration,
        String policyClass,
        String observationFingerprint) {
    public LateArrivalObservation {
        sourceEventRef = QualityContractSupport.reference(sourceEventRef, "sourceEventRef");
        affectedCheckpointRef = QualityContractSupport.reference(affectedCheckpointRef, "affectedCheckpointRef");
        affectedSnapshotRef = QualityContractSupport.reference(affectedSnapshotRef, "affectedSnapshotRef");
        Objects.requireNonNull(eventTime, "eventTime");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        Objects.requireNonNull(latenessDuration, "latenessDuration");
        policyClass = QualityContractSupport.token(policyClass, "policyClass", 32);
        observationFingerprint = QualityContractSupport.fingerprint(observationFingerprint, "observationFingerprint");
        if (latenessDuration.isNegative()) throw new IllegalArgumentException("lateness cannot be negative");
    }
}
