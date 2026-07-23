package com.jc.data.contract.v1.quality;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record DataQualityPolicy(
        String qualityPolicyVersion,
        Set<String> requiredChecks,
        Set<String> requiredMetrics,
        Set<DataQualityFailure> blockerFailureCodes,
        Map<String, DataQualityThreshold> thresholds,
        Duration lateArrivalTolerance,
        BigDecimal duplicateTolerance,
        ZeroDenominatorPolicy zeroDenominatorPolicy) {

    public static final String VERSION = "data-quality-policy-v1";

    public DataQualityPolicy {
        qualityPolicyVersion = QualityContractSupport.version(qualityPolicyVersion, "qualityPolicyVersion");
        requiredChecks = Set.copyOf(Objects.requireNonNull(requiredChecks, "requiredChecks"));
        requiredMetrics = Set.copyOf(Objects.requireNonNull(requiredMetrics, "requiredMetrics"));
        blockerFailureCodes = Set.copyOf(Objects.requireNonNull(blockerFailureCodes, "blockerFailureCodes"));
        thresholds = Map.copyOf(Objects.requireNonNull(thresholds, "thresholds"));
        Objects.requireNonNull(lateArrivalTolerance, "lateArrivalTolerance");
        duplicateTolerance = QualityContractSupport.decimal(duplicateTolerance, "duplicateTolerance");
        Objects.requireNonNull(zeroDenominatorPolicy, "zeroDenominatorPolicy");
        if (lateArrivalTolerance.isNegative() || duplicateTolerance.signum() < 0) {
            throw new IllegalArgumentException("quality tolerances cannot be negative");
        }
    }

    public static DataQualityPolicy v1() {
        Set<String> checks = Set.of(
                "source.count", "source.set_fingerprint", "source.range", "source.membership", "source.contract",
                "source.timestamp", "source.checkpoint_fingerprint",
                "projection.records", "projection.source_count", "projection.aggregation", "projection.adapter_evidence",
                "snapshot.record_count", "snapshot.subject_count", "snapshot.source_count", "snapshot.content_fingerprint",
                "snapshot.lineage_fingerprint", "snapshot.contract", "snapshot.as_of", "snapshot.status",
                "lineage.completeness", "lineage.orphan", "lineage.duplicate", "lineage.source",
                "lineage.adapter_evidence", "lineage.binding", "lineage.fingerprint",
                "identity.binding", "exposure.binding",
                "rebuild.record_count", "rebuild.subject_count", "rebuild.source_count", "rebuild.records",
                "rebuild.snapshot_fingerprint", "rebuild.lineage_fingerprint", "rebuild.ordering");
        Set<String> metrics = Set.of(
                "source_completeness_rate", "projection_coverage_rate", "lineage_completeness_rate",
                "lineage_orphan_rate", "duplicate_source_rate", "duplicate_lineage_rate",
                "snapshot_record_reconciliation_rate", "snapshot_subject_reconciliation_rate",
                "snapshot_source_reconciliation_rate", "fingerprint_match_rate",
                "identity_binding_valid_rate", "exposure_binding_valid_rate", "late_arrival_rate",
                "conflict_rate", "rebuild_match_rate");
        Map<String, DataQualityThreshold> thresholds = Map.ofEntries(
                Map.entry("source_completeness_rate", atLeastOne("source_completeness_rate")),
                Map.entry("projection_coverage_rate", atLeastOne("projection_coverage_rate")),
                Map.entry("lineage_completeness_rate", atLeastOne("lineage_completeness_rate")),
                Map.entry("lineage_orphan_rate", atMostZero("lineage_orphan_rate")),
                Map.entry("duplicate_source_rate", atMostZero("duplicate_source_rate")),
                Map.entry("duplicate_lineage_rate", atMostZero("duplicate_lineage_rate")),
                Map.entry("snapshot_record_reconciliation_rate", atLeastOne("snapshot_record_reconciliation_rate")),
                Map.entry("snapshot_subject_reconciliation_rate", atLeastOne("snapshot_subject_reconciliation_rate")),
                Map.entry("snapshot_source_reconciliation_rate", atLeastOne("snapshot_source_reconciliation_rate")),
                Map.entry("fingerprint_match_rate", atLeastOne("fingerprint_match_rate")),
                Map.entry("identity_binding_valid_rate", atLeastOne("identity_binding_valid_rate")),
                Map.entry("exposure_binding_valid_rate", atLeastOne("exposure_binding_valid_rate")),
                Map.entry("late_arrival_rate", atMostZero("late_arrival_rate")),
                Map.entry("conflict_rate", atMostZero("conflict_rate")),
                Map.entry("rebuild_match_rate", atLeastOne("rebuild_match_rate")));
        return new DataQualityPolicy(VERSION, checks, metrics, Set.of(
                DataQualityFailure.PRIVACY_POLICY_VIOLATION,
                DataQualityFailure.IDENTITY_NAMESPACE_CONFLICT,
                DataQualityFailure.GENERAL_EXPOSURE_USED_AS_P2,
                DataQualityFailure.SNAPSHOT_CONTENT_FINGERPRINT_MISMATCH,
                DataQualityFailure.SNAPSHOT_LINEAGE_FINGERPRINT_MISMATCH,
                DataQualityFailure.NON_DETERMINISTIC_OUTPUT), thresholds, Duration.ZERO, BigDecimal.ZERO,
                ZeroDenominatorPolicy.NOT_APPLICABLE);
    }

    private static DataQualityThreshold atLeastOne(String name) {
        return new DataQualityThreshold(name, BigDecimal.ONE, DataQualityThresholdOperator.GREATER_THAN_OR_EQUAL);
    }

    private static DataQualityThreshold atMostZero(String name) {
        return new DataQualityThreshold(name, BigDecimal.ZERO, DataQualityThresholdOperator.LESS_THAN_OR_EQUAL);
    }
}
