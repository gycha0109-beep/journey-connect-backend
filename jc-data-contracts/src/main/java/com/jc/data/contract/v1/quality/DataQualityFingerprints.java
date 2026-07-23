package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ProjectionFingerprints;
import com.jc.data.contract.v1.projection.ProjectionLineage;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataQualityFingerprints {
    public static final String VALIDATION_INPUT_DOMAIN = "data-quality-validation-input-sha256-v1";
    public static final String CHECK_DOMAIN = "data-quality-check-evidence-sha256-v1";
    public static final String METRIC_DOMAIN = "data-quality-metric-sha256-v1";
    public static final String VERDICT_DOMAIN = "data-quality-verdict-sha256-v1";
    public static final String REBUILD_DOMAIN = "data-quality-rebuild-comparison-sha256-v1";
    public static final String LATE_DOMAIN = "data-quality-late-arrival-observation-sha256-v1";

    private DataQualityFingerprints() { }

    public static String validationInput(DataQualityValidationContext context) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("snapshotRef", context.snapshot().snapshotRef());
        fields.put("snapshotFingerprint", context.snapshot().contentFingerprint());
        fields.put("snapshotLineageFingerprint", context.snapshot().lineageFingerprint());
        fields.put("checkpointRef", context.checkpoint().checkpointRef());
        fields.put("sourceSetFingerprint", context.checkpoint().sourceSetFingerprint());
        fields.put("projectionSchemaVersion", context.definition().projectionSchemaVersion());
        fields.put("projectionPolicyVersion", context.definition().projectionPolicyVersion());
        fields.put("identityBindingVersion", context.projectionDefinition().identityBindingVersion());
        fields.put("targetContractVersion", context.projectionDefinition().targetContractVersion());
        fields.put("validatorVersion", context.definition().validatorVersion());
        fields.put("qualityPolicyVersion", context.definition().qualityPolicyVersion());
        fields.put("validationScope", context.definition().validationScope().name());
        fields.put("sourceFingerprints", context.checkpoint().sources().stream()
                .map(source -> source.sourceFingerprint()).sorted().toList());
        fields.put("recordFingerprints", context.projectionRecords().stream().map(ProjectionRecord::projectionRecordFingerprint).sorted().toList());
        fields.put("lineageFingerprints", context.lineage().stream().map(ProjectionLineage::lineageEntryFingerprint).sorted().toList());
        fields.put("identityFingerprints", context.identityBindings().stream().map(IdentityBindingEvidence::authoritativeFingerprint).sorted().toList());
        fields.put("exposureFingerprints", context.exposureEvidence().stream().map(e -> e.binding().exposureFingerprint()).sorted().toList());
        return ProjectionFingerprints.fingerprint(VALIDATION_INPUT_DOMAIN, fields);
    }

    public static String check(String code, DataQualityValidationScope scope, String expected, String observed,
            String difference, DataQualitySeverity severity, DataQualityCheckStatus status,
            DataQualityFailure failure, String reason, boolean required) {
        return ProjectionFingerprints.fingerprint(CHECK_DOMAIN, Map.ofEntries(
                Map.entry("checkCode", code), Map.entry("scope", scope.name()),
                Map.entry("expected", expected), Map.entry("observed", observed),
                Map.entry("difference", difference), Map.entry("severity", severity.name()),
                Map.entry("status", status.name()),
                Map.entry("failure", failure == null ? "" : failure.wireValue()),
                Map.entry("reason", reason == null ? "" : reason), Map.entry("required", required)));
    }

    public static String metric(String name, long numerator, long denominator, String value, String unit,
            DataQualityMetricStatus status, DataQualityThreshold threshold) {
        return ProjectionFingerprints.fingerprint(METRIC_DOMAIN, Map.of(
                "metricName", name, "numerator", numerator, "denominator", denominator,
                "metricValue", value, "metricUnit", unit, "threshold", threshold.threshold(),
                "operator", threshold.operator().name(), "thresholdResult", status.name(),
                "metricVersion", "data-quality-metric-v1"));
    }

    public static String verdict(SnapshotQualityStatus status, long blockers, long errors, long warnings,
            long passed, long failed, long skipped, String score, String policyVersion) {
        return ProjectionFingerprints.fingerprint(VERDICT_DOMAIN, Map.ofEntries(
                Map.entry("overallStatus", status.name()), Map.entry("blockerCount", blockers),
                Map.entry("errorCount", errors), Map.entry("warningCount", warnings),
                Map.entry("passedCheckCount", passed), Map.entry("failedCheckCount", failed),
                Map.entry("skippedRequiredCheckCount", skipped), Map.entry("qualityScore", score),
                Map.entry("qualityPolicyVersion", policyVersion)));
    }

    public static String rebuild(Map<String, ?> fields) {
        return ProjectionFingerprints.fingerprint(REBUILD_DOMAIN, fields);
    }

    public static String late(Map<String, ?> fields) {
        return ProjectionFingerprints.fingerprint(LATE_DOMAIN, fields);
    }
}
