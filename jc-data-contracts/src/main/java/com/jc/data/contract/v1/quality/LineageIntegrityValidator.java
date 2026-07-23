package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ProjectionFingerprints;
import com.jc.data.contract.v1.projection.ProjectionLineage;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LineageIntegrityValidator {
    public List<DataQualityCheckResult> validate(DataQualityValidationContext context) {
        Map<String, ProjectionRecord> records = new LinkedHashMap<>();
        for (ProjectionRecord record : context.projectionRecords()) records.put(record.recordRef(), record);
        Map<String, ProjectionSourceEvent> sources = new LinkedHashMap<>();
        for (ProjectionSourceEvent source : context.sourceEvents()) {
            sources.put(source.sourceEventRef() + "|" + source.sourceFingerprint(), source);
        }
        List<DataQualityCheckResult> checks = new ArrayList<>();
        Set<String> recordsWithLineage = new HashSet<>();
        Set<String> identities = new HashSet<>();
        boolean orphan = false;
        boolean duplicate = false;
        boolean sourceMissing = false;
        boolean sourceFingerprintMismatch = false;
        boolean adapterMismatch = false;
        boolean bindingMismatch = false;
        boolean entryFingerprintMismatch = false;
        for (ProjectionLineage lineage : context.lineage()) {
            recordsWithLineage.add(lineage.projectionRecordRef());
            if (!records.containsKey(lineage.projectionRecordRef())) orphan = true;
            String identity = lineage.projectionRecordRef() + "|" + lineage.sourceEventRef() + "|" + lineage.sourceFingerprint();
            if (!identities.add(identity)) duplicate = true;
            ProjectionSourceEvent source = sources.get(lineage.sourceEventRef() + "|" + lineage.sourceFingerprint());
            if (source == null) {
                sourceMissing = true;
                if (context.sourceEvents().stream().anyMatch(event -> event.sourceEventRef().equals(lineage.sourceEventRef()))) {
                    sourceFingerprintMismatch = true;
                }
            } else {
                if (!java.util.Objects.equals(source.adapterEvidenceRef(), lineage.adapterEvidenceRef())
                        || !java.util.Objects.equals(source.mappingPolicyVersion(), lineage.mappingPolicyVersion())) {
                    adapterMismatch = true;
                }
            }
            if (!lineage.sourceCheckpointRef().equals(context.checkpoint().checkpointRef())
                    || !lineage.projectionPolicyVersion().equals(context.projectionDefinition().projectionPolicyVersion())) {
                bindingMismatch = true;
            }
            if (!ProjectionFingerprints.lineageEntryFingerprint(lineage.canonicalFields())
                    .equals(lineage.lineageEntryFingerprint())) entryFingerprintMismatch = true;
        }
        boolean complete = recordsWithLineage.containsAll(records.keySet()) && !records.isEmpty();
        checks.add(complete ? QualityChecks.pass("lineage.completeness", DataQualityValidationScope.LINEAGE_INTEGRITY,
                records.keySet().toString(), recordsWithLineage.toString(), true)
                : QualityChecks.fail("lineage.completeness", DataQualityValidationScope.LINEAGE_INTEGRITY,
                records.keySet().toString(), recordsWithLineage.toString(), "missing", DataQualitySeverity.BLOCKER,
                DataQualityFailure.LINEAGE_MISSING, true));
        checks.add(orphan ? QualityChecks.fail("lineage.orphan", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "0", "1", "1", DataQualitySeverity.BLOCKER, DataQualityFailure.LINEAGE_ORPHAN, true)
                : QualityChecks.pass("lineage.orphan", DataQualityValidationScope.LINEAGE_INTEGRITY, "0", "0", true));
        checks.add(duplicate ? QualityChecks.fail("lineage.duplicate", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "0", "1", "1", DataQualitySeverity.BLOCKER, DataQualityFailure.LINEAGE_DUPLICATE, true)
                : QualityChecks.pass("lineage.duplicate", DataQualityValidationScope.LINEAGE_INTEGRITY, "0", "0", true));
        DataQualityFailure sourceFailure = sourceFingerprintMismatch
                ? DataQualityFailure.LINEAGE_SOURCE_FINGERPRINT_MISMATCH : DataQualityFailure.LINEAGE_SOURCE_MISSING;
        checks.add(sourceMissing ? QualityChecks.fail("lineage.source", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "all_sources_present", "missing", "1", DataQualitySeverity.BLOCKER, sourceFailure, true)
                : QualityChecks.pass("lineage.source", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "all_sources_present", "all_sources_present", true));
        checks.add(adapterMismatch ? QualityChecks.fail("lineage.adapter_evidence", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "exact", "mismatch", "1", DataQualitySeverity.BLOCKER,
                DataQualityFailure.LINEAGE_ADAPTER_EVIDENCE_MISMATCH, true)
                : QualityChecks.pass("lineage.adapter_evidence", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "exact", "exact", true));
        checks.add(bindingMismatch ? QualityChecks.fail("lineage.binding", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "checkpoint_and_policy_exact", "mismatch", "1", DataQualitySeverity.BLOCKER,
                DataQualityFailure.LINEAGE_CHECKPOINT_MISMATCH, true)
                : QualityChecks.pass("lineage.binding", DataQualityValidationScope.LINEAGE_INTEGRITY,
                "checkpoint_and_policy_exact", "checkpoint_and_policy_exact", true));
        String total = ProjectionFingerprints.lineageFingerprint(context.lineage());
        boolean totalMismatch = !total.equals(context.snapshot().lineageFingerprint());
        checks.add((entryFingerprintMismatch || totalMismatch)
                ? QualityChecks.fail("lineage.fingerprint", DataQualityValidationScope.LINEAGE_INTEGRITY,
                context.snapshot().lineageFingerprint(), total, "different", DataQualitySeverity.BLOCKER,
                DataQualityFailure.LINEAGE_FINGERPRINT_MISMATCH, true)
                : QualityChecks.pass("lineage.fingerprint", DataQualityValidationScope.LINEAGE_INTEGRITY,
                context.snapshot().lineageFingerprint(), total, true));
        return List.copyOf(checks);
    }
}
