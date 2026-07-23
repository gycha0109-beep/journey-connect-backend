package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ProjectionFingerprints;
import com.jc.data.contract.v1.projection.ProjectionSnapshot;
import com.jc.data.contract.v1.projection.ProjectionSnapshotStatus;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotConsistencyValidator {
    public List<DataQualityCheckResult> validate(DataQualityValidationContext context) {
        ProjectionSnapshot snapshot = context.snapshot();
        List<DataQualityCheckResult> checks = new ArrayList<>();
        long records = context.projectionRecords().size();
        long subjects = context.projectionRecords().stream().map(record -> record.subjectRef()).distinct().count();
        long sources = context.lineage().stream().map(lineage -> lineage.sourceEventRef() + "|" + lineage.sourceFingerprint())
                .distinct().count();
        checks.add(count("snapshot.record_count", snapshot.recordCount(), records,
                DataQualityFailure.SNAPSHOT_RECORD_COUNT_MISMATCH));
        checks.add(count("snapshot.subject_count", snapshot.subjectCount(), subjects,
                DataQualityFailure.SNAPSHOT_SUBJECT_COUNT_MISMATCH));
        checks.add(count("snapshot.source_count", snapshot.sourceEventCount(), sources,
                DataQualityFailure.SNAPSHOT_SOURCE_COUNT_MISMATCH));
        String content = ProjectionFingerprints.snapshotFingerprint(context.projectionDefinition(),
                context.checkpoint().checkpointRef(), snapshot.snapshotAsOf(), context.projectionRecords());
        checks.add(fp("snapshot.content_fingerprint", snapshot.contentFingerprint(), content,
                DataQualityFailure.SNAPSHOT_CONTENT_FINGERPRINT_MISMATCH));
        String lineage = ProjectionFingerprints.lineageFingerprint(context.lineage());
        checks.add(fp("snapshot.lineage_fingerprint", snapshot.lineageFingerprint(), lineage,
                DataQualityFailure.SNAPSHOT_LINEAGE_FINGERPRINT_MISMATCH));
        boolean contract = snapshot.projectionName().equals(context.definition().projectionName())
                && snapshot.projectionSchemaVersion().equals(context.definition().projectionSchemaVersion())
                && snapshot.projectionPolicyVersion().equals(context.definition().projectionPolicyVersion())
                && snapshot.sourceCheckpointRef().equals(context.definition().sourceCheckpointRef());
        checks.add(contract ? QualityChecks.pass("snapshot.contract", DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                "exact", "exact", true) : QualityChecks.fail("snapshot.contract",
                DataQualityValidationScope.SNAPSHOT_CONSISTENCY, "exact", "mismatch", "1",
                DataQualitySeverity.BLOCKER, snapshot.sourceCheckpointRef().equals(context.definition().sourceCheckpointRef())
                ? DataQualityFailure.SNAPSHOT_CONTRACT_MISMATCH : DataQualityFailure.SNAPSHOT_CHECKPOINT_MISMATCH, true));
        checks.add(snapshot.snapshotAsOf().equals(context.definition().validationAsOf())
                ? QualityChecks.pass("snapshot.as_of", DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                context.definition().validationAsOf().toString(), snapshot.snapshotAsOf().toString(), true)
                : QualityChecks.fail("snapshot.as_of", DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                context.definition().validationAsOf().toString(), snapshot.snapshotAsOf().toString(), "different",
                DataQualitySeverity.BLOCKER, DataQualityFailure.SNAPSHOT_AS_OF_MISMATCH, true));
        boolean status = snapshot.snapshotStatus() == ProjectionSnapshotStatus.CREATED
                || snapshot.snapshotStatus() == ProjectionSnapshotStatus.VALIDATED;
        checks.add(status ? QualityChecks.pass("snapshot.status", DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                "CREATED_OR_VALIDATED", snapshot.snapshotStatus().name(), true)
                : QualityChecks.fail("snapshot.status", DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                "CREATED_OR_VALIDATED", snapshot.snapshotStatus().name(), "invalid", DataQualitySeverity.BLOCKER,
                DataQualityFailure.SNAPSHOT_STATUS_INVALID, true));
        return List.copyOf(checks);
    }

    private static DataQualityCheckResult count(String code, long expected, long observed, DataQualityFailure failure) {
        return expected == observed ? QualityChecks.pass(code, DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                Long.toString(expected), Long.toString(observed), true)
                : QualityChecks.fail(code, DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                Long.toString(expected), Long.toString(observed), Long.toString(observed - expected),
                DataQualitySeverity.BLOCKER, failure, true);
    }

    private static DataQualityCheckResult fp(String code, String expected, String observed, DataQualityFailure failure) {
        return expected.equals(observed) ? QualityChecks.pass(code, DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                expected, observed, true) : QualityChecks.fail(code, DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
                expected, observed, "different", DataQualitySeverity.BLOCKER, failure, true);
    }
}
