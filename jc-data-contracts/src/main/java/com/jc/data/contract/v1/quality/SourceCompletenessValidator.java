package com.jc.data.contract.v1.quality;

import com.jc.data.contract.support.Sha256DigestV1;
import com.jc.data.contract.v1.projection.CheckpointSource;
import com.jc.data.contract.v1.projection.ProjectionFingerprints;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import com.jc.data.contract.v1.projection.SourceCheckpoint;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SourceCompletenessValidator {
    public List<DataQualityCheckResult> validate(DataQualityValidationContext context) {
        SourceCheckpoint checkpoint = context.checkpoint();
        LinkedHashMap<String, ProjectionSourceEvent> unique = new LinkedHashMap<>();
        boolean conflictingDuplicate = false;
        boolean eventOutOfRange = false;
        for (ProjectionSourceEvent event : context.sourceEvents()) {
            boolean occurredOutOfRange = event.occurredAt().isBefore(checkpoint.eventTimeFrom())
                    || !event.occurredAt().isBefore(checkpoint.eventTimeTo());
            if (occurredOutOfRange) {
                eventOutOfRange = true;
                continue;
            }
            if (event.ingestedAt().isAfter(checkpoint.ingestedAtUpperBound())) {
                continue;
            }
            String key = event.sourceEventRef() + '\u001f'
                    + (event.adapterEvidenceRef() == null ? "" : event.adapterEvidenceRef());
            ProjectionSourceEvent previous = unique.putIfAbsent(key, event);
            if (previous != null && !previous.equals(event)) {
                conflictingDuplicate = true;
            }
        }
        List<ProjectionSourceEvent> events = unique.values().stream()
                .sorted(Comparator.comparing(ProjectionSourceEvent::occurredAt)
                        .thenComparing(ProjectionSourceEvent::sourceEventRef)
                        .thenComparing(ProjectionSourceEvent::sourceFingerprint))
                .toList();
        List<CheckpointSource> actual = events.stream().map(event -> new CheckpointSource(
                event.sourceEventRef(), event.sourceFingerprint(), event.adapterEvidenceRef(),
                event.occurredAt(), event.ingestedAt())).toList();
        boolean fingerprints = events.stream().allMatch(event -> Sha256DigestV1.lowercaseHex(
                event.sourceCanonicalForm().getBytes(StandardCharsets.UTF_8)).equals(event.sourceFingerprint()));
        List<DataQualityCheckResult> checks = new ArrayList<>();
        checks.add(compareLong("source.count", checkpoint.sourceEventCount(), actual.size(),
                DataQualityFailure.SOURCE_COUNT_MISMATCH));
        String actualSet = !fingerprints ? "source_payload_mismatch"
                : actual.isEmpty() ? "empty" : ProjectionFingerprints.sourceSetFingerprint(actual);
        checks.add(compare("source.set_fingerprint", checkpoint.sourceSetFingerprint(), actualSet,
                DataQualityFailure.SOURCE_SET_FINGERPRINT_MISMATCH, DataQualitySeverity.BLOCKER));

        Map<String, CheckpointSource> expectedMembers = byIdentity(checkpoint.sources());
        Map<String, CheckpointSource> actualMembers = byIdentity(actual);
        boolean missing = expectedMembers.keySet().stream().anyMatch(key -> !actualMembers.containsKey(key));
        boolean unexpected = actualMembers.keySet().stream().anyMatch(key -> !expectedMembers.containsKey(key));
        if (missing || unexpected || conflictingDuplicate) {
            DataQualityFailure failure = missing ? DataQualityFailure.SOURCE_EVENT_MISSING
                    : DataQualityFailure.SOURCE_EVENT_UNEXPECTED;
            checks.add(QualityChecks.fail("source.membership", DataQualityValidationScope.SOURCE_COMPLETENESS,
                    expectedMembers.keySet().toString(), actualMembers.keySet().toString(),
                    "missing=" + missing + ",unexpected=" + unexpected + ",conflict=" + conflictingDuplicate,
                    DataQualitySeverity.BLOCKER, failure, true));
        } else {
            checks.add(QualityChecks.pass("source.membership", DataQualityValidationScope.SOURCE_COMPLETENESS,
                    expectedMembers.keySet().toString(), actualMembers.keySet().toString(), true));
        }

        boolean checkpointRangeValid = checkpoint.eventTimeFrom().isBefore(checkpoint.eventTimeTo());
        if (!checkpointRangeValid || eventOutOfRange) {
            checks.add(QualityChecks.fail("source.range", DataQualityValidationScope.SOURCE_COMPLETENESS,
                    "all_events_in_checkpoint_range", eventOutOfRange ? "event_out_of_range" : "invalid_checkpoint_range",
                    "1", DataQualitySeverity.BLOCKER,
                    eventOutOfRange ? DataQualityFailure.SOURCE_EVENT_OUT_OF_RANGE
                            : DataQualityFailure.SOURCE_RANGE_MISMATCH,
                    true));
        } else {
            checks.add(QualityChecks.pass("source.range", DataQualityValidationScope.SOURCE_COMPLETENESS,
                    "all_events_in_checkpoint_range", "all_events_in_checkpoint_range", true));
        }

        boolean contract = events.stream().allMatch(event -> checkpoint.sourceContractVersion().equals(event.sourceContractVersion()))
                && events.stream().allMatch(event -> checkpoint.sourceSchemaVersion().equals(event.sourceSchemaVersion()));
        checks.add(contract ? QualityChecks.pass("source.contract", DataQualityValidationScope.SOURCE_COMPLETENESS,
                checkpoint.sourceContractVersion() + "/" + checkpoint.sourceSchemaVersion(),
                checkpoint.sourceContractVersion() + "/" + checkpoint.sourceSchemaVersion(), true)
                : QualityChecks.fail("source.contract", DataQualityValidationScope.SOURCE_COMPLETENESS,
                checkpoint.sourceContractVersion() + "/" + checkpoint.sourceSchemaVersion(), "mismatch", "1",
                DataQualitySeverity.BLOCKER, events.stream().anyMatch(e -> !checkpoint.sourceContractVersion().equals(e.sourceContractVersion()))
                ? DataQualityFailure.SOURCE_CONTRACT_MISMATCH : DataQualityFailure.SOURCE_SCHEMA_MISMATCH, true));

        boolean timestamps = true;
        for (ProjectionSourceEvent event : events) {
            CheckpointSource member = expectedMembers.get(identity(new CheckpointSource(event.sourceEventRef(),
                    event.sourceFingerprint(), event.adapterEvidenceRef(), event.occurredAt(), event.ingestedAt())));
            if (member == null || !member.occurredAt().equals(event.occurredAt())
                    || !member.ingestedAt().equals(event.ingestedAt())) {
                timestamps = false;
            }
        }
        checks.add(timestamps ? QualityChecks.pass("source.timestamp", DataQualityValidationScope.SOURCE_COMPLETENESS,
                "authoritative", "authoritative", true)
                : QualityChecks.fail("source.timestamp", DataQualityValidationScope.SOURCE_COMPLETENESS,
                "authoritative", "mismatch", "1", DataQualitySeverity.BLOCKER,
                DataQualityFailure.SOURCE_TIMESTAMP_MISMATCH, true));
        String definition = ProjectionFingerprints.checkpointDefinitionFingerprint(
                checkpoint.sourceStream(), checkpoint.sourceContractVersion(), checkpoint.sourceSchemaVersion(),
                checkpoint.eventTimeFrom(), checkpoint.eventTimeTo(), checkpoint.ingestedAtUpperBound(),
                checkpoint.sourceEventCount(), checkpoint.sourceSetFingerprint());
        checks.add(compare("source.checkpoint_fingerprint", checkpoint.checkpointDefinitionFingerprint(), definition,
                DataQualityFailure.CHECKPOINT_DEFINITION_FINGERPRINT_MISMATCH, DataQualitySeverity.BLOCKER));
        return List.copyOf(checks);
    }

    private static DataQualityCheckResult compareLong(String code, long expected, long observed,
            DataQualityFailure failure) {
        return expected == observed
                ? QualityChecks.pass(code, DataQualityValidationScope.SOURCE_COMPLETENESS,
                        Long.toString(expected), Long.toString(observed), true)
                : QualityChecks.fail(code, DataQualityValidationScope.SOURCE_COMPLETENESS,
                        Long.toString(expected), Long.toString(observed), Long.toString(observed - expected),
                        DataQualitySeverity.BLOCKER, failure, true);
    }

    private static DataQualityCheckResult compare(String code, String expected, String observed,
            DataQualityFailure failure, DataQualitySeverity severity) {
        return expected.equals(observed)
                ? QualityChecks.pass(code, DataQualityValidationScope.SOURCE_COMPLETENESS, expected, observed, true)
                : QualityChecks.fail(code, DataQualityValidationScope.SOURCE_COMPLETENESS, expected, observed,
                        "different", severity, failure, true);
    }

    private static Map<String, CheckpointSource> byIdentity(List<CheckpointSource> sources) {
        LinkedHashMap<String, CheckpointSource> result = new LinkedHashMap<>();
        for (CheckpointSource source : sources) {
            result.put(identity(source), source);
        }
        return result;
    }

    private static String identity(CheckpointSource source) {
        return source.sourceEventRef() + "|" + source.sourceFingerprint() + "|"
                + (source.adapterEvidenceRef() == null ? "" : source.adapterEvidenceRef());
    }
}
