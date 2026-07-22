package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.Objects;

public record CheckpointSource(
        String sourceEventRef,
        String sourceFingerprint,
        String adapterEvidenceRef,
        Instant occurredAt,
        Instant ingestedAt) {

    public CheckpointSource {
        sourceEventRef = ProjectionEngineSupport.requireReference(sourceEventRef, "sourceEventRef");
        sourceFingerprint = ProjectionEngineSupport.requireFingerprint(sourceFingerprint, "sourceFingerprint");
        if (adapterEvidenceRef != null) {
            adapterEvidenceRef = ProjectionEngineSupport.requireReference(adapterEvidenceRef, "adapterEvidenceRef");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
    }
}
