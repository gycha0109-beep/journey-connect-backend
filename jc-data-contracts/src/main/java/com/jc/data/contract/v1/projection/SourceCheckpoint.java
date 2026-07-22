package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record SourceCheckpoint(
        String checkpointRef,
        String sourceStream,
        String sourceContractVersion,
        String sourceSchemaVersion,
        Instant eventTimeFrom,
        Instant eventTimeTo,
        Instant ingestedAtUpperBound,
        String lastSourceEventRef,
        long sourceEventCount,
        String sourceSetFingerprint,
        String checkpointDefinitionFingerprint,
        List<CheckpointSource> sources) {

    public SourceCheckpoint {
        checkpointRef = ProjectionEngineSupport.requireReference(checkpointRef, "checkpointRef");
        sourceStream = ProjectionEngineSupport.requireToken(sourceStream, "sourceStream", 96);
        sourceContractVersion = ProjectionEngineSupport.requireVersion(
                sourceContractVersion, "sourceContractVersion");
        sourceSchemaVersion = ProjectionEngineSupport.requireVersion(sourceSchemaVersion, "sourceSchemaVersion");
        Objects.requireNonNull(eventTimeFrom, "eventTimeFrom");
        Objects.requireNonNull(eventTimeTo, "eventTimeTo");
        Objects.requireNonNull(ingestedAtUpperBound, "ingestedAtUpperBound");
        if (!eventTimeFrom.isBefore(eventTimeTo)) {
            throw new IllegalArgumentException("checkpoint event range must be non-empty");
        }
        lastSourceEventRef = ProjectionEngineSupport.requireReference(lastSourceEventRef, "lastSourceEventRef");
        if (sourceEventCount < 1) {
            throw new IllegalArgumentException("sourceEventCount must be positive");
        }
        sourceSetFingerprint = ProjectionEngineSupport.requireFingerprint(
                sourceSetFingerprint, "sourceSetFingerprint");
        checkpointDefinitionFingerprint = ProjectionEngineSupport.requireFingerprint(
                checkpointDefinitionFingerprint, "checkpointDefinitionFingerprint");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (sources.size() != sourceEventCount) {
            throw new IllegalArgumentException("sourceEventCount does not match sources");
        }
    }

    public static SourceCheckpoint create(
            String checkpointRef,
            String sourceStream,
            String sourceContractVersion,
            String sourceSchemaVersion,
            Instant eventTimeFrom,
            Instant eventTimeTo,
            Instant ingestedAtUpperBound,
            List<ProjectionSourceEvent> sourceEvents) {
        Objects.requireNonNull(sourceEvents, "sourceEvents");
        List<CheckpointSource> included = sourceEvents.stream()
                .filter(event -> !event.occurredAt().isBefore(eventTimeFrom))
                .filter(event -> event.occurredAt().isBefore(eventTimeTo))
                .filter(event -> !event.ingestedAt().isAfter(ingestedAtUpperBound))
                .map(event -> new CheckpointSource(
                        event.sourceEventRef(), event.sourceFingerprint(), event.adapterEvidenceRef(),
                        event.occurredAt(), event.ingestedAt()))
                .distinct()
                .sorted(Comparator.comparing(CheckpointSource::occurredAt)
                        .thenComparing(CheckpointSource::sourceEventRef)
                        .thenComparing(CheckpointSource::sourceFingerprint))
                .toList();
        if (included.isEmpty()) {
            throw new IllegalArgumentException("checkpoint requires at least one included source");
        }
        String sourceSet = ProjectionFingerprints.sourceSetFingerprint(included);
        CheckpointSource last = included.getLast();
        String definition = ProjectionFingerprints.checkpointDefinitionFingerprint(
                sourceStream, sourceContractVersion, sourceSchemaVersion, eventTimeFrom, eventTimeTo,
                ingestedAtUpperBound, included.size(), sourceSet);
        return new SourceCheckpoint(
                checkpointRef, sourceStream, sourceContractVersion, sourceSchemaVersion,
                eventTimeFrom, eventTimeTo, ingestedAtUpperBound, last.sourceEventRef(),
                included.size(), sourceSet, definition, included);
    }

    public boolean matches(List<ProjectionSourceEvent> sourceEvents) {
        List<CheckpointSource> included = sourceEvents.stream()
                .filter(event -> !event.occurredAt().isBefore(eventTimeFrom))
                .filter(event -> event.occurredAt().isBefore(eventTimeTo))
                .filter(event -> !event.ingestedAt().isAfter(ingestedAtUpperBound))
                .map(event -> new CheckpointSource(
                        event.sourceEventRef(), event.sourceFingerprint(), event.adapterEvidenceRef(),
                        event.occurredAt(), event.ingestedAt()))
                .distinct()
                .sorted(Comparator.comparing(CheckpointSource::occurredAt)
                        .thenComparing(CheckpointSource::sourceEventRef)
                        .thenComparing(CheckpointSource::sourceFingerprint))
                .toList();
        return !included.isEmpty()
                && included.size() == sourceEventCount
                && ProjectionFingerprints.sourceSetFingerprint(included).equals(sourceSetFingerprint)
                && included.getLast().sourceEventRef().equals(lastSourceEventRef);
    }
}
