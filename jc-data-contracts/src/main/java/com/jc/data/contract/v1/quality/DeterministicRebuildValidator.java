package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ProjectionFingerprints;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeterministicRebuildValidator {
    public Validation validate(DataQualityValidationContext context) {
        ProjectionResult<? extends ProjectionRecord> rebuilt = QualityRebuildEngine.rebuild(context);
        List<DataQualityCheckResult> checks = new ArrayList<>();
        if (!rebuilt.isSuccess()) {
            for (String code : List.of("rebuild.record_count", "rebuild.subject_count", "rebuild.source_count",
                    "rebuild.records", "rebuild.snapshot_fingerprint", "rebuild.lineage_fingerprint",
                    "rebuild.ordering")) {
                checks.add(QualityChecks.skipped(code, DataQualityValidationScope.DETERMINISTIC_REBUILD,
                        "rebuild_failed", true));
            }
            String empty = ProjectionFingerprints.fingerprint("data-quality-rebuild-empty-v1", Map.of("failed", true));
            RebuildComparison comparison = new RebuildComparison(false, 0, context.projectionRecords().size(),
                    0, context.projectionRecords().stream().map(ProjectionRecord::subjectRef).distinct().count(),
                    0, context.lineage().stream().map(l -> l.sourceEventRef() + "|" + l.sourceFingerprint()).distinct().count(),
                    List.of(), context.projectionRecords().stream().map(ProjectionRecord::projectionRecordFingerprint).sorted().toList(),
                    empty, context.snapshot().contentFingerprint(), empty, context.snapshot().lineageFingerprint(),
                    DataQualityFingerprints.rebuild(Map.of("matched", false, "reason", rebuilt.failure().code().wireValue())));
            return new Validation(List.copyOf(checks), comparison);
        }
        List<String> expectedFp = rebuilt.records().stream()
                .sorted(java.util.Comparator.comparing(ProjectionRecord::recordRef)
                        .thenComparing(ProjectionRecord::projectionRecordFingerprint))
                .map(ProjectionRecord::projectionRecordFingerprint).toList();
        List<String> observedFp = context.projectionRecords().stream()
                .sorted(java.util.Comparator.comparing(ProjectionRecord::recordRef)
                        .thenComparing(ProjectionRecord::projectionRecordFingerprint))
                .map(ProjectionRecord::projectionRecordFingerprint).toList();
        long expectedSubjects = rebuilt.records().stream().map(ProjectionRecord::subjectRef).distinct().count();
        long observedSubjects = context.projectionRecords().stream().map(ProjectionRecord::subjectRef).distinct().count();
        long expectedSources = rebuilt.lineage().stream().map(l -> l.sourceEventRef() + "|" + l.sourceFingerprint()).distinct().count();
        long observedSources = context.lineage().stream().map(l -> l.sourceEventRef() + "|" + l.sourceFingerprint()).distinct().count();
        checks.add(count("rebuild.record_count", rebuilt.records().size(), context.projectionRecords().size(),
                DataQualityFailure.REBUILD_RECORD_COUNT_MISMATCH));
        checks.add(count("rebuild.subject_count", expectedSubjects, observedSubjects,
                DataQualityFailure.REBUILD_SUBJECT_COUNT_MISMATCH));
        checks.add(count("rebuild.source_count", expectedSources, observedSources,
                DataQualityFailure.REBUILD_SOURCE_COUNT_MISMATCH));
        boolean records = expectedFp.stream().sorted().toList().equals(observedFp.stream().sorted().toList());
        checks.add(records ? QualityChecks.pass("rebuild.records", DataQualityValidationScope.DETERMINISTIC_REBUILD,
                expectedFp.stream().sorted().toList().toString(), observedFp.stream().sorted().toList().toString(), true)
                : QualityChecks.fail("rebuild.records", DataQualityValidationScope.DETERMINISTIC_REBUILD,
                expectedFp.stream().sorted().toList().toString(), observedFp.stream().sorted().toList().toString(),
                "different", DataQualitySeverity.BLOCKER,
                DataQualityFailure.REBUILD_PROJECTION_FINGERPRINT_MISMATCH, true));
        checks.add(fingerprint("rebuild.snapshot_fingerprint", rebuilt.snapshot().contentFingerprint(),
                context.snapshot().contentFingerprint(), DataQualityFailure.REBUILD_SNAPSHOT_FINGERPRINT_MISMATCH));
        checks.add(fingerprint("rebuild.lineage_fingerprint", rebuilt.snapshot().lineageFingerprint(),
                context.snapshot().lineageFingerprint(), DataQualityFailure.REBUILD_LINEAGE_FINGERPRINT_MISMATCH));
        boolean ordering = expectedFp.equals(observedFp);
        checks.add(ordering ? QualityChecks.pass("rebuild.ordering", DataQualityValidationScope.DETERMINISTIC_REBUILD,
                expectedFp.toString(), observedFp.toString(), true)
                : QualityChecks.fail("rebuild.ordering", DataQualityValidationScope.DETERMINISTIC_REBUILD,
                expectedFp.toString(), observedFp.toString(), "different", DataQualitySeverity.BLOCKER,
                DataQualityFailure.REBUILD_ORDERING_MISMATCH, true));
        boolean matched = checks.stream().noneMatch(check -> check.checkStatus() == DataQualityCheckStatus.FAIL
                || check.checkStatus() == DataQualityCheckStatus.SKIPPED);
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("matched", matched);
        fields.put("expectedRecordCount", rebuilt.records().size());
        fields.put("observedRecordCount", context.projectionRecords().size());
        fields.put("expectedSubjectCount", expectedSubjects);
        fields.put("observedSubjectCount", observedSubjects);
        fields.put("expectedSourceCount", expectedSources);
        fields.put("observedSourceCount", observedSources);
        fields.put("expectedRecordFingerprints", expectedFp.stream().sorted().toList());
        fields.put("observedRecordFingerprints", observedFp.stream().sorted().toList());
        fields.put("expectedSnapshotFingerprint", rebuilt.snapshot().contentFingerprint());
        fields.put("observedSnapshotFingerprint", context.snapshot().contentFingerprint());
        fields.put("expectedLineageFingerprint", rebuilt.snapshot().lineageFingerprint());
        fields.put("observedLineageFingerprint", context.snapshot().lineageFingerprint());
        RebuildComparison comparison = new RebuildComparison(matched, rebuilt.records().size(),
                context.projectionRecords().size(), expectedSubjects, observedSubjects, expectedSources, observedSources,
                expectedFp.stream().sorted().toList(), observedFp.stream().sorted().toList(),
                rebuilt.snapshot().contentFingerprint(), context.snapshot().contentFingerprint(),
                rebuilt.snapshot().lineageFingerprint(), context.snapshot().lineageFingerprint(),
                DataQualityFingerprints.rebuild(fields));
        return new Validation(List.copyOf(checks), comparison);
    }

    private static DataQualityCheckResult count(String code, long expected, long observed, DataQualityFailure failure) {
        return expected == observed ? QualityChecks.pass(code, DataQualityValidationScope.DETERMINISTIC_REBUILD,
                Long.toString(expected), Long.toString(observed), true)
                : QualityChecks.fail(code, DataQualityValidationScope.DETERMINISTIC_REBUILD,
                Long.toString(expected), Long.toString(observed), Long.toString(observed - expected),
                DataQualitySeverity.BLOCKER, failure, true);
    }

    private static DataQualityCheckResult fingerprint(String code, String expected, String observed,
            DataQualityFailure failure) {
        return expected.equals(observed) ? QualityChecks.pass(code, DataQualityValidationScope.DETERMINISTIC_REBUILD,
                expected, observed, true) : QualityChecks.fail(code, DataQualityValidationScope.DETERMINISTIC_REBUILD,
                expected, observed, "different", DataQualitySeverity.BLOCKER, failure, true);
    }

    public record Validation(List<DataQualityCheckResult> checks, RebuildComparison comparison) {
        public Validation {
            checks = List.copyOf(checks);
            if (comparison == null) throw new NullPointerException("comparison");
        }
    }
}
