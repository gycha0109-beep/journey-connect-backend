package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.AdapterEvidenceState;
import com.jc.data.contract.v1.projection.ProjectionFailureCode;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionResult;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectionCompletenessValidator {
    public List<DataQualityCheckResult> validate(DataQualityValidationContext context) {
        ProjectionResult<? extends ProjectionRecord> rebuilt = QualityRebuildEngine.rebuild(context);
        List<DataQualityCheckResult> checks = new ArrayList<>();
        if (!rebuilt.isSuccess()) {
            DataQualityFailure failure = map(rebuilt.failure().code());
            checks.add(QualityChecks.fail("projection.records", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                    "rebuild_success", rebuilt.failure().code().wireValue(), "1", DataQualitySeverity.BLOCKER,
                    failure, true));
            checks.add(QualityChecks.skipped("projection.source_count", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                    "rebuild_failed", true));
            checks.add(QualityChecks.skipped("projection.aggregation", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                    "rebuild_failed", true));
        } else {
            Map<String, ProjectionRecord> expected = byRef(rebuilt.records());
            Map<String, ProjectionRecord> observed = byRef(context.projectionRecords());
            boolean missing = expected.keySet().stream().anyMatch(key -> !observed.containsKey(key));
            boolean unexpected = observed.keySet().stream().anyMatch(key -> !expected.containsKey(key))
                    || observed.size() != context.projectionRecords().size();
            if (missing || unexpected) {
                checks.add(QualityChecks.fail("projection.records", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                        expected.keySet().toString(), observed.keySet().toString(),
                        "missing=" + missing + ",unexpected=" + unexpected, DataQualitySeverity.BLOCKER,
                        missing ? DataQualityFailure.PROJECTION_RECORD_MISSING
                                : DataQualityFailure.PROJECTION_RECORD_UNEXPECTED, true));
            } else {
                checks.add(QualityChecks.pass("projection.records", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                        expected.keySet().toString(), observed.keySet().toString(), true));
            }
            boolean sourceCounts = expected.entrySet().stream().allMatch(entry -> {
                ProjectionRecord actual = observed.get(entry.getKey());
                return actual != null && actual.sourceEventCount() == entry.getValue().sourceEventCount();
            });
            checks.add(sourceCounts ? QualityChecks.pass("projection.source_count",
                    DataQualityValidationScope.PROJECTION_COMPLETENESS, "exact", "exact", true)
                    : QualityChecks.fail("projection.source_count", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                    "exact", "mismatch", "1", DataQualitySeverity.BLOCKER,
                    DataQualityFailure.PROJECTION_SOURCE_COUNT_MISMATCH, true));
            boolean aggregates = expected.entrySet().stream().allMatch(entry -> {
                ProjectionRecord actual = observed.get(entry.getKey());
                return actual != null
                        && actual.projectionRecordFingerprint().equals(entry.getValue().projectionRecordFingerprint())
                        && actual.canonicalFields().equals(entry.getValue().canonicalFields());
            });
            checks.add(aggregates ? QualityChecks.pass("projection.aggregation",
                    DataQualityValidationScope.PROJECTION_COMPLETENESS, "exact", "exact", true)
                    : QualityChecks.fail("projection.aggregation", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                    "exact", "mismatch", "1", DataQualitySeverity.BLOCKER,
                    DataQualityFailure.PROJECTION_AGGREGATION_MISMATCH, true));
        }
        DataQualityFailure adapterFailure = invalidAdapterFailure(context);
        checks.add(adapterFailure == null ? QualityChecks.pass("projection.adapter_evidence",
                DataQualityValidationScope.PROJECTION_COMPLETENESS, "mapped_or_none", "mapped_or_none", true)
                : QualityChecks.fail("projection.adapter_evidence", DataQualityValidationScope.PROJECTION_COMPLETENESS,
                "mapped_or_none", adapterFailure.wireValue(), "1", DataQualitySeverity.BLOCKER,
                adapterFailure, true));
        return List.copyOf(checks);
    }

    private static Map<String, ProjectionRecord> byRef(List<? extends ProjectionRecord> records) {
        LinkedHashMap<String, ProjectionRecord> result = new LinkedHashMap<>();
        for (ProjectionRecord record : records) result.put(record.recordRef(), record);
        return result;
    }

    private static DataQualityFailure invalidAdapterFailure(DataQualityValidationContext context) {
        java.util.Set<String> checkpointMembers = context.checkpoint().sources().stream()
                .map(source -> source.sourceEventRef() + "|" + source.sourceFingerprint() + "|"
                        + (source.adapterEvidenceRef() == null ? "" : source.adapterEvidenceRef()))
                .collect(java.util.stream.Collectors.toSet());
        for (ProjectionSourceEvent source : context.sourceEvents()) {
            String identity = source.sourceEventRef() + "|" + source.sourceFingerprint() + "|"
                    + (source.adapterEvidenceRef() == null ? "" : source.adapterEvidenceRef());
            if (!checkpointMembers.contains(identity)) continue;
            if (source.adapterEvidenceState() == AdapterEvidenceState.CONFLICTED) {
                return DataQualityFailure.CONFLICTED_ADAPTER_EVIDENCE_INCLUDED;
            }
            if (source.adapterEvidenceState() == AdapterEvidenceState.REJECTED) {
                return DataQualityFailure.REJECTED_ADAPTER_EVIDENCE_INCLUDED;
            }
            if (source.adapterEvidenceState() == AdapterEvidenceState.UNSUPPORTED) {
                return DataQualityFailure.UNSUPPORTED_ADAPTER_EVIDENCE_INCLUDED;
            }
        }
        return null;
    }

    private static DataQualityFailure map(ProjectionFailureCode code) {
        return switch (code) {
            case SOURCE_EVENT_MISSING -> DataQualityFailure.PROJECTION_RECORD_MISSING;
            case SOURCE_CHECKPOINT_INVALID -> DataQualityFailure.PROJECTION_WINDOW_VIOLATION;
            case SOURCE_FINGERPRINT_MISMATCH -> DataQualityFailure.SOURCE_SET_FINGERPRINT_MISMATCH;
            case ADAPTER_EVIDENCE_CONFLICTED -> DataQualityFailure.CONFLICTED_ADAPTER_EVIDENCE_INCLUDED;
            case ADAPTER_EVIDENCE_REJECTED -> DataQualityFailure.REJECTED_ADAPTER_EVIDENCE_INCLUDED;
            case IDENTITY_BINDING_REQUIRED -> DataQualityFailure.IDENTITY_BINDING_MISSING;
            case IDENTITY_BINDING_INVALID -> DataQualityFailure.IDENTITY_BINDING_INVALID;
            case IDENTITY_NAMESPACE_CONFLICT -> DataQualityFailure.IDENTITY_NAMESPACE_CONFLICT;
            case EXPOSURE_BINDING_MISSING -> DataQualityFailure.EXPOSURE_BINDING_MISSING;
            case EXPOSURE_BINDING_INVALID -> DataQualityFailure.EXPOSURE_BINDING_INVALID;
            case OUTCOME_WINDOW_VIOLATION -> DataQualityFailure.OUTCOME_WINDOW_VIOLATION;
            case PRIVACY_POLICY_VIOLATION -> DataQualityFailure.PRIVACY_POLICY_VIOLATION;
            default -> DataQualityFailure.PROJECTION_AGGREGATION_MISMATCH;
        };
    }
}
