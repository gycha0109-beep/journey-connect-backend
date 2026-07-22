package com.jc.data.contract.v1.projection;

import com.jc.data.contract.support.Sha256DigestV1;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public final class RecommendationProfileProjectionEngine {
    private static final Set<String> POSITIVE = Set.of(
            "recommendation_click", "post_like", "post_bookmark", "post_share",
            "follow", "tag_click", "crew_join");
    private static final Set<String> NEGATIVE = Set.of(
            "post_unlike", "post_unbookmark", "post_hide", "post_report",
            "unfollow", "crew_leave");

    public ProjectionResult<RecommendationProfileInputProjection> project(
            ProjectionDefinition definition,
            SourceCheckpoint checkpoint,
            List<ProjectionSourceEvent> sourceEvents,
            List<IdentityBinding> identityBindings,
            Instant projectionAsOf,
            ProjectionIdentifiers identifiers,
            String producerBuildId,
            Instant createdAt) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(sourceEvents, "sourceEvents");
        Objects.requireNonNull(identityBindings, "identityBindings");
        Objects.requireNonNull(projectionAsOf, "projectionAsOf");
        Objects.requireNonNull(identifiers, "identifiers");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!ProjectionDefinition.PROFILE_NAME.equals(definition.projectionName())
                || !ProjectionDefinition.PROFILE_NAME.equals(definition.projectionSchemaVersion())) {
            return failure(ProjectionFailureCode.UNSUPPORTED_PROJECTION_SCHEMA, "definition", "profile_schema");
        }
        if (!checkpoint.matches(sourceEvents)) {
            return failure(ProjectionFailureCode.SOURCE_CHECKPOINT_INVALID, "checkpoint", "source_set");
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
        Map<String, IdentityBinding> bindings;
        try {
            bindings = bindingIndex(identityBindings);
        } catch (IllegalArgumentException exception) {
            return failure(ProjectionFailureCode.IDENTITY_NAMESPACE_CONFLICT, "identity", "binding_conflict");
        }
        TreeMap<String, List<ProjectionSourceEvent>> bySubject = new TreeMap<>();
        for (ProjectionSourceEvent event : selected) {
            ProjectionFailure sourceFailure = validateSource(event, checkpoint, projectionAsOf);
            if (sourceFailure != null) {
                return ProjectionResult.failure(sourceFailure);
            }
            String subject = resolveSubject(event.identityRef(), definition.identityBindingVersion(), bindings);
            if (subject == null) {
                return failure(ProjectionFailureCode.IDENTITY_BINDING_REQUIRED, "identity", "binding_required");
            }
            bySubject.computeIfAbsent(subject, ignored -> new ArrayList<>()).add(event);
        }
        if (bySubject.isEmpty()) {
            return failure(ProjectionFailureCode.SOURCE_EVENT_MISSING, "source", "no_included_event");
        }

        ArrayList<RecommendationProfileInputProjection> records = new ArrayList<>();
        ArrayList<ProjectionLineage> allLineage = new ArrayList<>();
        for (Map.Entry<String, List<ProjectionSourceEvent>> entry : bySubject.entrySet()) {
            List<ProjectionSourceEvent> subjectSources = stableEvents(entry.getValue());
            for (Integer windowDays : definition.activityWindowsDays()) {
                Instant from = projectionAsOf.minus(windowDays.longValue(), ChronoUnit.DAYS);
                List<ProjectionSourceEvent> windowSources = subjectSources.stream()
                        .filter(event -> !event.occurredAt().isBefore(from))
                        .filter(event -> event.occurredAt().isBefore(projectionAsOf))
                        .toList();
                if (windowSources.isEmpty()) {
                    continue;
                }
                String recordRef = deterministicRef("profile_record", Map.of(
                        "subjectRef", entry.getKey(), "windowDays", windowDays,
                        "checkpointRef", checkpoint.checkpointRef(),
                        "projectionPolicyVersion", definition.projectionPolicyVersion()));
                List<ProjectionLineage> lineage = lineage(
                        identifiers.snapshotRef(), recordRef, checkpoint, definition, windowSources);
                String lineageFingerprint = ProjectionFingerprints.lineageFingerprint(lineage);
                Map<String, Long> interactions = counts(windowSources, ProjectionSourceEvent::eventType);
                List<RankedReference> regions = ranked(windowSources, ProjectionSourceEvent::regionRef,
                        definition.maxRankedReferences());
                List<RankedReference> contents = ranked(windowSources, ProjectionSourceEvent::contentRef,
                        definition.maxRankedReferences());
                List<RankedReference> tags = rankedTags(windowSources, definition.maxRankedReferences());
                Map<String, Long> positive = signalCounts(windowSources, POSITIVE);
                Map<String, Long> negative = signalCounts(windowSources, NEGATIVE);
                LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
                canonical.put("projectionName", ProjectionDefinition.PROFILE_NAME);
                canonical.put("subjectRef", entry.getKey());
                canonical.put("projectionAsOf", projectionAsOf);
                canonical.put("sourceCheckpointRef", checkpoint.checkpointRef());
                canonical.put("profileSchemaVersion", definition.projectionSchemaVersion());
                canonical.put("projectionPolicyVersion", definition.projectionPolicyVersion());
                canonical.put("activityWindowDays", windowDays);
                canonical.put("interactionCounts", interactions);
                canonical.put("recentRegions", regions.stream().map(RankedReference::canonicalFields).toList());
                canonical.put("recentContentRefs", contents.stream().map(RankedReference::canonicalFields).toList());
                canonical.put("recentTagRefs", tags.stream().map(RankedReference::canonicalFields).toList());
                canonical.put("engagementSignals", positive);
                canonical.put("negativeSignals", negative);
                canonical.put("sourceEventCount", windowSources.size());
                canonical.put("sourceLineageFingerprint", lineageFingerprint);
                String recordFingerprint = ProjectionFingerprints.recordFingerprint(canonical);
                records.add(new RecommendationProfileInputProjection(
                        recordRef, entry.getKey(), projectionAsOf, checkpoint.checkpointRef(),
                        definition.projectionSchemaVersion(), definition.projectionPolicyVersion(), windowDays,
                        interactions, regions, contents, tags, positive, negative, windowSources.size(),
                        lineageFingerprint, recordFingerprint));
                allLineage.addAll(lineage);
            }
        }
        if (records.isEmpty() || allLineage.isEmpty()) {
            return failure(ProjectionFailureCode.LINEAGE_INCOMPLETE, "lineage", "no_window_lineage");
        }
        return success(definition, checkpoint, projectionAsOf, identifiers, producerBuildId, createdAt,
                records, allLineage);
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

    private static Map<String, IdentityBinding> bindingIndex(List<IdentityBinding> bindings) {
        TreeMap<String, IdentityBinding> result = new TreeMap<>();
        for (IdentityBinding binding : bindings) {
            IdentityBinding previous = result.putIfAbsent(binding.sourceIdentityRef(), binding);
            if (previous != null && !previous.equals(binding)) {
                throw new IllegalArgumentException("conflicting identity binding");
            }
        }
        return Map.copyOf(result);
    }

    private static String resolveSubject(
            String identityRef, String bindingVersion, Map<String, IdentityBinding> bindings) {
        if (identityRef.startsWith("subject:")) {
            return identityRef;
        }
        IdentityBinding binding = bindings.get(identityRef);
        if (binding == null || !binding.bindingVersion().equals(bindingVersion)) {
            return null;
        }
        return binding.targetSubjectRef();
    }

    private static List<ProjectionLineage> lineage(
            String snapshotRef,
            String recordRef,
            SourceCheckpoint checkpoint,
            ProjectionDefinition definition,
            List<ProjectionSourceEvent> sources) {
        return stableEvents(sources).stream().map(source -> {
            LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
            fields.put("projectionRecordRef", recordRef);
            fields.put("sourceEventRef", source.sourceEventRef());
            fields.put("sourceFingerprint", source.sourceFingerprint());
            fields.put("adapterEvidenceRef", source.adapterEvidenceRef());
            fields.put("sourceCheckpointRef", checkpoint.checkpointRef());
            fields.put("projectionPolicyVersion", definition.projectionPolicyVersion());
            fields.put("mappingPolicyVersion", source.mappingPolicyVersion());
            return new ProjectionLineage(
                    snapshotRef, recordRef, source.sourceEventRef(), source.sourceFingerprint(),
                    source.adapterEvidenceRef(), checkpoint.checkpointRef(), definition.projectionPolicyVersion(),
                    source.mappingPolicyVersion(), ProjectionFingerprints.lineageEntryFingerprint(fields));
        }).toList();
    }

    private static Map<String, Long> counts(
            Collection<ProjectionSourceEvent> sources,
            Function<ProjectionSourceEvent, String> classifier) {
        TreeMap<String, Long> result = new TreeMap<>();
        for (ProjectionSourceEvent source : sources) {
            String value = classifier.apply(source);
            if (value != null) {
                result.merge(value, 1L, Long::sum);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, Long> signalCounts(
            Collection<ProjectionSourceEvent> sources, Set<String> accepted) {
        TreeMap<String, Long> result = new TreeMap<>();
        for (ProjectionSourceEvent source : sources) {
            if (accepted.contains(source.eventType())) {
                result.merge(source.eventType(), 1L, Long::sum);
            }
        }
        return Map.copyOf(result);
    }

    private static List<RankedReference> ranked(
            List<ProjectionSourceEvent> sources,
            Function<ProjectionSourceEvent, String> extractor,
            int limit) {
        HashMap<String, Aggregate> aggregates = new HashMap<>();
        for (ProjectionSourceEvent source : sources) {
            String reference = extractor.apply(source);
            if (reference != null) {
                aggregates.compute(reference, (ignored, current) -> current == null
                        ? new Aggregate(1L, source.occurredAt())
                        : current.add(source.occurredAt()));
            }
        }
        return aggregates.entrySet().stream()
                .map(entry -> new RankedReference(
                        entry.getKey(), entry.getValue().count(), entry.getValue().lastOccurredAt()))
                .sorted(Comparator.comparingLong(RankedReference::count).reversed()
                        .thenComparing(RankedReference::lastOccurredAt, Comparator.reverseOrder())
                        .thenComparing(RankedReference::reference))
                .limit(limit)
                .toList();
    }

    private static List<RankedReference> rankedTags(List<ProjectionSourceEvent> sources, int limit) {
        ArrayList<ProjectionSourceEvent> expanded = new ArrayList<>();
        for (ProjectionSourceEvent source : sources) {
            for (String tag : source.tagRefs()) {
                expanded.add(new ProjectionSourceEvent(
                        source.sourceEventRef(), source.sourceFingerprint(), source.sourceContractVersion(),
                        source.sourceSchemaVersion(), source.eventType(), source.occurredAt(), source.ingestedAt(),
                        source.identityRef(), source.sessionRef(), source.entityRef(), source.regionRef(), tag,
                        List.of(), source.exposureRef(), source.variantRef(), source.adapterEvidenceState(),
                        source.adapterEvidenceRef(), source.mappingPolicyVersion(), source.attributes(),
                        source.sourceCanonicalForm()));
            }
        }
        return ranked(expanded, ProjectionSourceEvent::contentRef, limit);
    }

    private static List<ProjectionSourceEvent> stableEvents(List<ProjectionSourceEvent> sources) {
        return sources.stream().sorted(Comparator.comparing(ProjectionSourceEvent::occurredAt)
                .thenComparing(ProjectionSourceEvent::sourceEventRef)
                .thenComparing(ProjectionSourceEvent::sourceFingerprint)).toList();
    }

    private static String deterministicRef(String namespace, Map<String, ?> fields) {
        return namespace + ':' + ProjectionFingerprints.fingerprint(namespace + "-v1", fields).substring(0, 32);
    }

    private static ProjectionResult<RecommendationProfileInputProjection> success(
            ProjectionDefinition definition,
            SourceCheckpoint checkpoint,
            Instant projectionAsOf,
            ProjectionIdentifiers identifiers,
            String producerBuildId,
            Instant createdAt,
            List<RecommendationProfileInputProjection> records,
            List<ProjectionLineage> lineage) {
        ProjectionRun run = new ProjectionRun(
                identifiers.runRef(), definition, checkpoint.checkpointRef(), checkpoint.eventTimeFrom(),
                checkpoint.eventTimeTo(), projectionAsOf, producerBuildId, createdAt);
        String contentFingerprint = ProjectionFingerprints.snapshotFingerprint(
                definition, checkpoint.checkpointRef(), projectionAsOf, records);
        String lineageFingerprint = ProjectionFingerprints.lineageFingerprint(lineage);
        long subjectCount = records.stream().map(RecommendationProfileInputProjection::subjectRef).distinct().count();
        long sourceCount = lineage.stream().map(ProjectionLineage::sourceEventRef).distinct().count();
        ProjectionSnapshot snapshot = new ProjectionSnapshot(
                identifiers.snapshotRef(), identifiers.runRef(), definition.projectionName(),
                definition.projectionSchemaVersion(), definition.projectionPolicyVersion(),
                checkpoint.checkpointRef(), projectionAsOf, records.size(), subjectCount, sourceCount,
                contentFingerprint, lineageFingerprint, ProjectionSnapshotStatus.CREATED,
                createdAt, "projection_evidence_90d", "data-retention-policy-v1",
                createdAt.plus(90, ChronoUnit.DAYS));
        return ProjectionResult.success(run, records, lineage, snapshot);
    }

    private static ProjectionResult<RecommendationProfileInputProjection> failure(
            ProjectionFailureCode code, String field, String detail) {
        return ProjectionResult.failure(new ProjectionFailure(code, field, detail));
    }

    private record Aggregate(long count, Instant lastOccurredAt) {
        private Aggregate add(Instant instant) {
            return new Aggregate(count + 1, instant.isAfter(lastOccurredAt) ? instant : lastOccurredAt);
        }
    }
}
