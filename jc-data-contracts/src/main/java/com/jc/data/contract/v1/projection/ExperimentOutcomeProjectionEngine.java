package com.jc.data.contract.v1.projection;

import com.jc.data.contract.support.Sha256DigestV1;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ExperimentOutcomeProjectionEngine {
    private static final Set<String> OUTCOME_TYPES = Set.of(
            "recommendation_click", "post_like", "post_bookmark", "post_share");

    public ProjectionResult<ExperimentOutcomeInputProjection> project(
            ProjectionDefinition definition,
            SourceCheckpoint checkpoint,
            List<ProjectionSourceEvent> sourceEvents,
            IdentityBinding identityBinding,
            ExperimentExposureBinding exposure,
            Instant projectionAsOf,
            ProjectionIdentifiers identifiers,
            String producerBuildId,
            Instant createdAt) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(sourceEvents, "sourceEvents");
        Objects.requireNonNull(projectionAsOf, "projectionAsOf");
        Objects.requireNonNull(identifiers, "identifiers");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!ProjectionDefinition.OUTCOME_NAME.equals(definition.projectionName())
                || !ProjectionDefinition.OUTCOME_NAME.equals(definition.projectionSchemaVersion())) {
            return failure(ProjectionFailureCode.UNSUPPORTED_PROJECTION_SCHEMA, "definition", "outcome_schema");
        }
        if (exposure == null) {
            return failure(ProjectionFailureCode.EXPOSURE_BINDING_MISSING, "exposure", "missing");
        }
        if (!ExperimentExposureBinding.AUTHORITY.equals(exposure.authorityId())) {
            return failure(ProjectionFailureCode.EXPOSURE_BINDING_INVALID, "exposure", "authority");
        }
        if (identityBinding == null) {
            return failure(ProjectionFailureCode.IDENTITY_BINDING_REQUIRED, "identity", "binding_required");
        }
        if (!identityBinding.sourceIdentityRef().equals(exposure.sourceUserRef())
                || !identityBinding.targetSubjectRef().equals(exposure.targetSubjectRef())
                || !identityBinding.bindingVersion().equals(definition.identityBindingVersion())) {
            return failure(ProjectionFailureCode.IDENTITY_BINDING_INVALID, "identity", "exposure_binding");
        }
        if (!checkpoint.matches(sourceEvents)) {
            return failure(ProjectionFailureCode.SOURCE_CHECKPOINT_INVALID, "checkpoint", "source_set");
        }
        if (projectionAsOf.isBefore(exposure.exposedAt())) {
            return failure(ProjectionFailureCode.EXPOSURE_BINDING_INVALID, "projectionAsOf", "before_exposure");
        }

        List<ProjectionSourceEvent> selected;
        try {
            selected = ProjectionFingerprints.distinctStableSources(sourceEvents.stream()
                    .filter(event -> checkpoint.sources().stream().anyMatch(source ->
                            source.sourceEventRef().equals(event.sourceEventRef())
                                    && source.sourceFingerprint().equals(event.sourceFingerprint())))
                    .toList());
        } catch (IllegalArgumentException exception) {
            return failure(ProjectionFailureCode.PROJECTION_INVARIANT_FAILED, "source", "duplicate_conflict");
        }
        if (selected.isEmpty()) {
            return failure(ProjectionFailureCode.SOURCE_EVENT_MISSING, "source", "no_included_event");
        }

        ArrayList<ProjectionSourceEvent> lineageSources = new ArrayList<>();
        ArrayList<ProjectionSourceEvent> outcomes = new ArrayList<>();
        Instant outcomeEnd = exposure.exposedAt().plus(definition.outcomeWindow());
        boolean exposureSourceSeen = false;
        for (ProjectionSourceEvent event : selected) {
            ProjectionFailure sourceFailure = validateSource(event, checkpoint, projectionAsOf);
            if (sourceFailure != null) {
                return ProjectionResult.failure(sourceFailure);
            }
            String subject = resolveSubject(event.identityRef(), identityBinding);
            if (!exposure.targetSubjectRef().equals(subject)) {
                return failure(ProjectionFailureCode.IDENTITY_NAMESPACE_CONFLICT, "identity", "subject_mismatch");
            }
            if ("experiment_exposure".equals(event.eventType())) {
                if (!exposure.exposureRef().equals(event.exposureRef())
                        || !exposure.variantRef().equals(event.variantRef())
                        || !event.occurredAt().equals(exposure.exposedAt())) {
                    return failure(ProjectionFailureCode.EXPOSURE_BINDING_INVALID, "exposure", "source_mismatch");
                }
                exposureSourceSeen = true;
                lineageSources.add(event);
                continue;
            }
            if (!OUTCOME_TYPES.contains(event.eventType())) {
                continue;
            }
            if (!exposure.exposureRef().equals(event.exposureRef())
                    || !exposure.variantRef().equals(event.variantRef())
                    || !exposure.sessionRef().equals(event.sessionRef())) {
                return failure(ProjectionFailureCode.EXPOSURE_BINDING_INVALID, "outcome", "binding_mismatch");
            }
            if (event.occurredAt().isBefore(exposure.exposedAt()) || !event.occurredAt().isBefore(outcomeEnd)) {
                return failure(ProjectionFailureCode.OUTCOME_WINDOW_VIOLATION, "outcome", "outside_window");
            }
            outcomes.add(event);
            lineageSources.add(event);
        }
        if (!exposureSourceSeen) {
            return failure(ProjectionFailureCode.EXPOSURE_BINDING_MISSING, "exposure", "source_missing");
        }
        lineageSources.sort(Comparator.comparing(ProjectionSourceEvent::occurredAt)
                .thenComparing(ProjectionSourceEvent::sourceEventRef));
        outcomes.sort(Comparator.comparing(ProjectionSourceEvent::occurredAt)
                .thenComparing(ProjectionSourceEvent::sourceEventRef));

        String recordRef = "outcome_record:" + ProjectionFingerprints.fingerprint(
                "experiment-outcome-record-reference-v1", Map.of(
                        "exposureRef", exposure.exposureRef(),
                        "checkpointRef", checkpoint.checkpointRef(),
                        "projectionPolicyVersion", definition.projectionPolicyVersion())).substring(0, 32);
        List<ProjectionLineage> lineage = lineageSources.stream().map(source -> {
            LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
            fields.put("projectionRecordRef", recordRef);
            fields.put("sourceEventRef", source.sourceEventRef());
            fields.put("sourceFingerprint", source.sourceFingerprint());
            fields.put("adapterEvidenceRef", source.adapterEvidenceRef());
            fields.put("sourceCheckpointRef", checkpoint.checkpointRef());
            fields.put("projectionPolicyVersion", definition.projectionPolicyVersion());
            fields.put("mappingPolicyVersion", source.mappingPolicyVersion());
            return new ProjectionLineage(
                    identifiers.snapshotRef(), recordRef, source.sourceEventRef(), source.sourceFingerprint(),
                    source.adapterEvidenceRef(), checkpoint.checkpointRef(), definition.projectionPolicyVersion(),
                    source.mappingPolicyVersion(), ProjectionFingerprints.lineageEntryFingerprint(fields));
        }).toList();
        String sourceLineageFingerprint = ProjectionFingerprints.lineageFingerprint(lineage);
        List<String> refs = outcomes.stream().map(ProjectionSourceEvent::sourceEventRef).toList();
        boolean clicked = has(outcomes, "recommendation_click");
        boolean liked = has(outcomes, "post_like");
        boolean saved = has(outcomes, "post_bookmark");
        boolean shared = has(outcomes, "post_share");
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("projectionName", ProjectionDefinition.OUTCOME_NAME);
        canonical.put("experimentRef", exposure.experimentRef());
        canonical.put("experimentVersion", exposure.experimentVersion());
        canonical.put("variantRef", exposure.variantRef());
        canonical.put("exposureRef", exposure.exposureRef());
        canonical.put("runRef", exposure.runRef());
        canonical.put("subjectRef", exposure.targetSubjectRef());
        canonical.put("sessionRef", exposure.sessionRef());
        canonical.put("exposedAt", exposure.exposedAt());
        canonical.put("outcomeWindowSeconds", definition.outcomeWindow().toSeconds());
        canonical.put("clicked", clicked);
        canonical.put("liked", liked);
        canonical.put("saved", saved);
        canonical.put("shared", shared);
        canonical.put("fallbackObserved", exposure.fallbackObserved());
        canonical.put("outcomeEventRefs", refs);
        canonical.put("sourceCheckpointRef", checkpoint.checkpointRef());
        canonical.put("sourceEventCount", lineageSources.size());
        canonical.put("sourceLineageFingerprint", sourceLineageFingerprint);
        String recordFingerprint = ProjectionFingerprints.recordFingerprint(canonical);
        ExperimentOutcomeInputProjection record = new ExperimentOutcomeInputProjection(
                recordRef, exposure.experimentRef(), exposure.experimentVersion(), exposure.variantRef(),
                exposure.exposureRef(), exposure.runRef(), exposure.targetSubjectRef(), exposure.sessionRef(),
                exposure.exposedAt(), definition.outcomeWindow().toSeconds(), clicked, liked, saved, shared,
                exposure.fallbackObserved(), refs, checkpoint.checkpointRef(), lineageSources.size(),
                sourceLineageFingerprint, recordFingerprint);
        ProjectionRun run = new ProjectionRun(
                identifiers.runRef(), definition, checkpoint.checkpointRef(), checkpoint.eventTimeFrom(),
                checkpoint.eventTimeTo(), projectionAsOf, producerBuildId, createdAt);
        String contentFingerprint = ProjectionFingerprints.snapshotFingerprint(
                definition, checkpoint.checkpointRef(), projectionAsOf, List.of(record));
        ProjectionSnapshot snapshot = new ProjectionSnapshot(
                identifiers.snapshotRef(), identifiers.runRef(), definition.projectionName(),
                definition.projectionSchemaVersion(), definition.projectionPolicyVersion(),
                checkpoint.checkpointRef(), projectionAsOf, 1L, 1L, lineageSources.size(),
                contentFingerprint, sourceLineageFingerprint, ProjectionSnapshotStatus.CREATED,
                createdAt, "projection_evidence_90d", "data-retention-policy-v1",
                createdAt.plus(90, ChronoUnit.DAYS));
        return ProjectionResult.success(run, List.of(record), lineage, snapshot);
    }

    private static ProjectionFailure validateSource(
            ProjectionSourceEvent event, SourceCheckpoint checkpoint, Instant projectionAsOf) {
        if (!event.sourceContractVersion().equals(checkpoint.sourceContractVersion())
                || !event.sourceSchemaVersion().equals(checkpoint.sourceSchemaVersion())) {
            return new ProjectionFailure(
                    ProjectionFailureCode.UNSUPPORTED_SOURCE_SCHEMA, "source", "schema_version");
        }
        String actual = Sha256DigestV1.lowercaseHex(
                event.sourceCanonicalForm().getBytes(StandardCharsets.UTF_8));
        if (!actual.equals(event.sourceFingerprint())) {
            return new ProjectionFailure(
                    ProjectionFailureCode.SOURCE_FINGERPRINT_MISMATCH, "source", "fingerprint");
        }
        if (!event.occurredAt().isBefore(projectionAsOf)) {
            return new ProjectionFailure(
                    ProjectionFailureCode.SOURCE_CHECKPOINT_INVALID, "occurredAt", "future_event");
        }
        return switch (event.adapterEvidenceState()) {
            case CONFLICTED -> new ProjectionFailure(
                    ProjectionFailureCode.ADAPTER_EVIDENCE_CONFLICTED, "adapter", "conflicted");
            case REJECTED -> new ProjectionFailure(
                    ProjectionFailureCode.ADAPTER_EVIDENCE_REJECTED, "adapter", "rejected");
            case UNSUPPORTED -> new ProjectionFailure(
                    ProjectionFailureCode.UNSUPPORTED_SOURCE_SCHEMA, "adapter", "unsupported");
            default -> null;
        };
    }

    private static String resolveSubject(String identityRef, IdentityBinding binding) {
        if (identityRef.startsWith("subject:")) {
            return identityRef;
        }
        return binding.sourceIdentityRef().equals(identityRef) ? binding.targetSubjectRef() : null;
    }

    private static boolean has(List<ProjectionSourceEvent> outcomes, String type) {
        return outcomes.stream().anyMatch(event -> type.equals(event.eventType()));
    }

    private static ProjectionResult<ExperimentOutcomeInputProjection> failure(
            ProjectionFailureCode code, String field, String detail) {
        return ProjectionResult.failure(new ProjectionFailure(code, field, detail));
    }
}
