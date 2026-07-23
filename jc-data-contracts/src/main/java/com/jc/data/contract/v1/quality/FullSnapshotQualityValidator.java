package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ExperimentExposureBinding;
import com.jc.data.contract.v1.projection.ExperimentOutcomeInputProjection;
import com.jc.data.contract.v1.projection.ProjectionDefinition;
import com.jc.data.contract.v1.projection.ProjectionLineage;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class FullSnapshotQualityValidator {
    private static final List<DataQualityValidationScope> ORDER = List.of(
            DataQualityValidationScope.SOURCE_COMPLETENESS,
            DataQualityValidationScope.PROJECTION_COMPLETENESS,
            DataQualityValidationScope.SNAPSHOT_CONSISTENCY,
            DataQualityValidationScope.LINEAGE_INTEGRITY,
            DataQualityValidationScope.IDENTITY_INTEGRITY,
            DataQualityValidationScope.EXPOSURE_INTEGRITY,
            DataQualityValidationScope.DETERMINISTIC_REBUILD);

    public DataQualityValidationResult validate(String validationRunRef, DataQualityValidationContext context) {
        if (context.definition().validationScope() != DataQualityValidationScope.FULL) {
            throw new IllegalArgumentException(DataQualityFailure.UNSUPPORTED_VALIDATION_SCOPE.wireValue());
        }
        if (!"data-quality-validator-v1".equals(context.definition().validatorVersion())) {
            throw new IllegalArgumentException(DataQualityFailure.UNSUPPORTED_VALIDATOR_VERSION.wireValue());
        }
        String inputFingerprint = DataQualityFingerprints.validationInput(context);
        DataQualityValidationRun run = new DataQualityValidationRun(validationRunRef, context.definition(),
                inputFingerprint, context.definition().validationAsOf(), "data_quality_evidence_90d",
                "data-retention-policy-v1", context.definition().validationAsOf().plus(90, ChronoUnit.DAYS));

        ArrayList<DataQualityCheckResult> checks = new ArrayList<>();
        checks.addAll(new SourceCompletenessValidator().validate(context));
        checks.addAll(new ProjectionCompletenessValidator().validate(context));
        checks.addAll(new SnapshotConsistencyValidator().validate(context));
        checks.addAll(new LineageIntegrityValidator().validate(context));
        checks.addAll(new IdentityIntegrityValidator().validate(context));
        checks.addAll(new ExposureIntegrityValidator().validate(context));
        DeterministicRebuildValidator.Validation rebuild = new DeterministicRebuildValidator().validate(context);
        checks.addAll(rebuild.checks());
        checks.sort(Comparator.comparingInt((DataQualityCheckResult check) -> ORDER.indexOf(check.checkScope()))
                .thenComparing(DataQualityCheckResult::checkCode)
                .thenComparing(DataQualityCheckResult::evidenceFingerprint));

        List<LateArrivalObservation> lateArrivals = lateArrivals(context);
        List<DataQualityMetric> metrics = metrics(context, checks, rebuild.comparison(), lateArrivals);
        List<DataQualityAnomaly> anomalies = checks.stream()
                .filter(check -> check.checkStatus() == DataQualityCheckStatus.FAIL)
                .map(check -> new DataQualityAnomaly(check.checkScope(), check.failureCode(), check.severity(),
                        "quality_check:" + check.checkCode().replace('.', '_'), check.evidenceFingerprint()))
                .toList();
        SnapshotQualityVerdict verdict = verdict(validationRunRef, context, checks, metrics);
        return new DataQualityValidationResult(run, checks, metrics, anomalies, lateArrivals,
                rebuild.comparison(), verdict);
    }

    private static List<LateArrivalObservation> lateArrivals(DataQualityValidationContext context) {
        return context.sourceEvents().stream()
                .filter(event -> !event.occurredAt().isBefore(context.checkpoint().eventTimeFrom()))
                .filter(event -> event.occurredAt().isBefore(context.checkpoint().eventTimeTo()))
                .filter(event -> event.ingestedAt().isAfter(context.checkpoint().ingestedAtUpperBound()))
                .sorted(Comparator.comparing(ProjectionSourceEvent::sourceEventRef))
                .map(event -> {
                    Duration lateness = Duration.between(context.checkpoint().ingestedAtUpperBound(), event.ingestedAt());
                    String policyClass = lateness.compareTo(context.qualityPolicy().lateArrivalTolerance()) <= 0
                            ? "WITHIN_TOLERANCE" : "REBUILD_REQUIRED";
                    LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
                    fields.put("sourceEventRef", event.sourceEventRef());
                    fields.put("affectedCheckpointRef", context.checkpoint().checkpointRef());
                    fields.put("affectedSnapshotRef", context.snapshot().snapshotRef());
                    fields.put("eventTime", event.occurredAt());
                    fields.put("ingestedAt", event.ingestedAt());
                    fields.put("latenessSeconds", lateness.toSeconds());
                    fields.put("policyClass", policyClass);
                    return new LateArrivalObservation(event.sourceEventRef(), context.checkpoint().checkpointRef(),
                            context.snapshot().snapshotRef(), event.occurredAt(), event.ingestedAt(), lateness,
                            policyClass, DataQualityFingerprints.late(fields));
                }).toList();
    }

    private static List<DataQualityMetric> metrics(DataQualityValidationContext context,
            List<DataQualityCheckResult> checks, RebuildComparison rebuild, List<LateArrivalObservation> late) {
        DataQualityMetricCalculator calculator = new DataQualityMetricCalculator();
        DataQualityPolicy policy = context.qualityPolicy();
        long checkpointCount = context.checkpoint().sourceEventCount();
        long actualCheckpointMembers = context.sourceEvents().stream()
                .filter(event -> !event.occurredAt().isBefore(context.checkpoint().eventTimeFrom()))
                .filter(event -> event.occurredAt().isBefore(context.checkpoint().eventTimeTo()))
                .filter(event -> !event.ingestedAt().isAfter(context.checkpoint().ingestedAtUpperBound()))
                .map(event -> event.sourceEventRef() + "|" + event.sourceFingerprint()).distinct().count();
        long recordsWithLineage = context.lineage().stream().map(ProjectionLineage::projectionRecordRef).distinct().count();
        long orphan = context.lineage().stream().filter(lineage -> context.projectionRecords().stream()
                .noneMatch(record -> record.recordRef().equals(lineage.projectionRecordRef()))).count();
        long duplicateLineage = context.lineage().size() - context.lineage().stream()
                .map(lineage -> lineage.projectionRecordRef() + "|" + lineage.sourceEventRef() + "|" + lineage.sourceFingerprint())
                .distinct().count();
        long conflictingSource = conflictingDuplicateSourceCount(context.sourceEvents());

        Set<String> lineageSources = context.lineage().stream()
                .map(lineage -> lineage.sourceEventRef() + "|" + lineage.sourceFingerprint())
                .collect(Collectors.toSet());
        Set<String> requiredUsers = context.sourceEvents().stream()
                .filter(event -> lineageSources.contains(event.sourceEventRef() + "|" + event.sourceFingerprint()))
                .map(ProjectionSourceEvent::identityRef)
                .filter(identity -> identity.startsWith("user:"))
                .collect(Collectors.toSet());
        long identityValid = requiredUsers.stream().filter(user -> validIdentityBinding(context, user)).count();

        List<ExperimentOutcomeInputProjection> outcomes = context.projectionRecords().stream()
                .filter(ExperimentOutcomeInputProjection.class::isInstance)
                .map(ExperimentOutcomeInputProjection.class::cast)
                .toList();
        boolean outcomeProjection = ProjectionDefinition.OUTCOME_NAME.equals(
                context.projectionDefinition().projectionName());
        long exposureRequired = outcomeProjection ? outcomes.size() : 0;
        long exposureValid = outcomeProjection
                ? outcomes.stream().filter(record -> validExposureBinding(context, record)).count() : 0;

        long fingerprintPass = checks.stream().filter(check -> check.checkCode().contains("fingerprint")
                && check.checkStatus() == DataQualityCheckStatus.PASS).count();
        long fingerprintTotal = checks.stream().filter(check -> check.checkCode().contains("fingerprint")).count();
        long projectionCovered = Math.min(rebuild.observedRecordCount(), rebuild.expectedRecordCount());
        List<DataQualityMetric> result = new ArrayList<>();
        result.add(calculator.calculate("source_completeness_rate",
                Math.min(actualCheckpointMembers, checkpointCount), checkpointCount, policy));
        result.add(calculator.calculate("projection_coverage_rate", projectionCovered,
                rebuild.expectedRecordCount(), policy));
        result.add(calculator.calculate("lineage_completeness_rate",
                Math.min(recordsWithLineage, context.projectionRecords().size()),
                context.projectionRecords().size(), policy));
        result.add(calculator.calculate("lineage_orphan_rate", orphan, context.lineage().size(), policy));
        result.add(calculator.calculate("duplicate_source_rate", conflictingSource,
                context.sourceEvents().size(), policy));
        result.add(calculator.calculate("duplicate_lineage_rate", duplicateLineage,
                context.lineage().size(), policy));
        result.add(binary(calculator, "snapshot_record_reconciliation_rate", checks,
                "snapshot.record_count", policy));
        result.add(binary(calculator, "snapshot_subject_reconciliation_rate", checks,
                "snapshot.subject_count", policy));
        result.add(binary(calculator, "snapshot_source_reconciliation_rate", checks,
                "snapshot.source_count", policy));
        result.add(calculator.calculate("fingerprint_match_rate", fingerprintPass, fingerprintTotal, policy));
        result.add(calculator.calculate("identity_binding_valid_rate", identityValid, requiredUsers.size(), policy));
        result.add(calculator.calculate("exposure_binding_valid_rate", exposureValid, exposureRequired, policy));
        result.add(calculator.calculate("late_arrival_rate", late.size(), context.sourceEvents().size(), policy));
        result.add(calculator.calculate("conflict_rate", conflictingSource, context.sourceEvents().size(), policy));
        result.add(calculator.calculate("rebuild_match_rate", rebuild.matched() ? 1 : 0, 1, policy));
        return result.stream().sorted(Comparator.comparing(DataQualityMetric::metricName)).toList();
    }

    private static boolean validIdentityBinding(DataQualityValidationContext context, String user) {
        List<IdentityBindingEvidence> matches = context.identityBindings().stream()
                .filter(evidence -> evidence.binding().sourceIdentityRef().equals(user)).toList();
        if (matches.size() != 1) {
            return false;
        }
        IdentityBindingEvidence evidence = matches.getFirst();
        return evidence.binding().bindingFingerprint().equals(evidence.authoritativeFingerprint())
                && evidence.sourceCheckpointRef().equals(context.checkpoint().checkpointRef())
                && ("journey-connect".equals(evidence.binding().bindingScope())
                    || context.projectionDefinition().targetContractVersion().equals(
                            evidence.binding().bindingScope()))
                && evidence.binding().targetSubjectRef().equals(evidence.projectionSubjectRef())
                && context.projectionRecords().stream().anyMatch(record -> record.subjectRef()
                        .equals(evidence.projectionSubjectRef()));
    }

    private static boolean validExposureBinding(DataQualityValidationContext context,
            ExperimentOutcomeInputProjection record) {
        List<P2ExposureEvidence> matches = context.exposureEvidence().stream()
                .filter(evidence -> evidence.binding().exposureRef().equals(record.exposureRef())).toList();
        if (matches.size() != 1) {
            return false;
        }
        P2ExposureEvidence evidence = matches.getFirst();
        ExperimentExposureBinding binding = evidence.binding();
        return evidence.authoritative() && !evidence.generalExposure()
                && evidence.fallbackAuthorityMatched()
                && ExperimentExposureBinding.AUTHORITY.equals(binding.authorityId())
                && binding.targetSubjectRef().equals(record.subjectRef())
                && binding.variantRef().equals(record.variantRef())
                && binding.experimentRef().equals(record.experimentRef())
                && binding.experimentVersion().equals(record.experimentVersion())
                && binding.exposedAt().equals(record.exposedAt())
                && binding.fallbackObserved() == record.fallbackObserved();
    }

    private static long conflictingDuplicateSourceCount(List<ProjectionSourceEvent> events) {
        Map<String, ProjectionSourceEvent> seen = new LinkedHashMap<>();
        long conflicts = 0;
        for (ProjectionSourceEvent event : events) {
            String key = event.sourceEventRef() + "|"
                    + (event.adapterEvidenceRef() == null ? "" : event.adapterEvidenceRef());
            ProjectionSourceEvent previous = seen.putIfAbsent(key, event);
            if (previous != null && !previous.equals(event)) {
                conflicts++;
            }
        }
        return conflicts;
    }

    private static DataQualityMetric binary(DataQualityMetricCalculator calculator, String metric,
            List<DataQualityCheckResult> checks, String checkCode, DataQualityPolicy policy) {
        boolean pass = checks.stream().anyMatch(check -> check.checkCode().equals(checkCode)
                && check.checkStatus() == DataQualityCheckStatus.PASS);
        return calculator.calculate(metric, pass ? 1 : 0, 1, policy);
    }

    private static SnapshotQualityVerdict verdict(String validationRunRef, DataQualityValidationContext context,
            List<DataQualityCheckResult> checks, List<DataQualityMetric> metrics) {
        return new SnapshotQualityVerdictEvaluator().evaluate(context.snapshot().snapshotRef(), validationRunRef,
                context.qualityPolicy(), checks, metrics);
    }
}
