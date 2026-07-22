package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.Objects;

public record ProjectionRun(
        String projectionRunRef,
        ProjectionDefinition definition,
        String sourceCheckpointRef,
        Instant sourceFrom,
        Instant sourceTo,
        Instant projectionAsOf,
        String producerBuildId,
        Instant createdAt) {

    public ProjectionRun {
        projectionRunRef = ProjectionEngineSupport.requireReference(projectionRunRef, "projectionRunRef");
        Objects.requireNonNull(definition, "definition");
        sourceCheckpointRef = ProjectionEngineSupport.requireReference(
                sourceCheckpointRef, "sourceCheckpointRef");
        Objects.requireNonNull(sourceFrom, "sourceFrom");
        Objects.requireNonNull(sourceTo, "sourceTo");
        Objects.requireNonNull(projectionAsOf, "projectionAsOf");
        producerBuildId = ProjectionEngineSupport.requireToken(producerBuildId, "producerBuildId", 128);
        Objects.requireNonNull(createdAt, "createdAt");
        if (!sourceFrom.isBefore(sourceTo) || projectionAsOf.isBefore(sourceFrom)) {
            throw new IllegalArgumentException("projection run time boundary is invalid");
        }
    }
}
