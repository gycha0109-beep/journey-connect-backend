package com.jc.data.contract.v1.quality;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SnapshotQualityVerdictEvaluator {
    public SnapshotQualityVerdict evaluate(
            String snapshotRef,
            String validationRunRef,
            DataQualityPolicy policy,
            List<DataQualityCheckResult> checks,
            List<DataQualityMetric> metrics) {
        Objects.requireNonNull(policy, "policy");
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics"));

        long blockers = checks.stream().filter(check -> check.checkStatus() == DataQualityCheckStatus.FAIL
                && check.severity() == DataQualitySeverity.BLOCKER).count();
        long errors = checks.stream().filter(check -> check.checkStatus() == DataQualityCheckStatus.FAIL
                && check.severity() == DataQualitySeverity.ERROR).count();
        long warnings = checks.stream().filter(check -> check.checkStatus() == DataQualityCheckStatus.FAIL
                && check.severity() == DataQualitySeverity.WARNING).count();
        long passed = checks.stream().filter(check -> check.checkStatus() == DataQualityCheckStatus.PASS).count();
        long failed = checks.stream().filter(check -> check.checkStatus() == DataQualityCheckStatus.FAIL).count();
        long skipped = checks.stream().filter(check -> check.required()
                && check.checkStatus() == DataQualityCheckStatus.SKIPPED).count();

        Set<String> observedChecks = checks.stream().map(DataQualityCheckResult::checkCode).collect(Collectors.toSet());
        Set<String> observedMetrics = metrics.stream().map(DataQualityMetric::metricName).collect(Collectors.toSet());
        boolean missingRequiredCheck = !observedChecks.containsAll(policy.requiredChecks());
        boolean missingRequiredMetric = !observedMetrics.containsAll(policy.requiredMetrics());
        boolean requiredCheckFailed = checks.stream().anyMatch(check -> check.required()
                && check.checkStatus() == DataQualityCheckStatus.FAIL);
        boolean requiredEvidenceUnavailable = checks.stream().anyMatch(check -> check.required()
                && check.checkStatus() == DataQualityCheckStatus.NOT_APPLICABLE
                && !isPolicyDefinedNotApplicable(check.checkCode()));
        boolean requiredMetricFailed = metrics.stream().anyMatch(metric -> policy.requiredMetrics().contains(metric.metricName())
                && metric.thresholdResult() == DataQualityMetricStatus.FAIL);
        boolean requiredMetricUndefined = metrics.stream().anyMatch(metric -> policy.requiredMetrics().contains(metric.metricName())
                && (metric.thresholdResult() == DataQualityMetricStatus.UNDEFINED));

        SnapshotQualityStatus status;
        if (blockers > 0 || requiredCheckFailed || requiredMetricFailed) {
            status = SnapshotQualityStatus.REJECTED;
        } else if (skipped > 0 || requiredEvidenceUnavailable || requiredMetricUndefined
                || missingRequiredCheck || missingRequiredMetric) {
            status = SnapshotQualityStatus.INCONCLUSIVE;
        } else {
            status = SnapshotQualityStatus.VALIDATED;
        }

        long considered = passed + failed + skipped;
        BigDecimal score = considered == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(passed)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(considered), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        String fingerprint = DataQualityFingerprints.verdict(status, blockers, errors, warnings, passed, failed,
                skipped, score.toPlainString(), policy.qualityPolicyVersion());
        return new SnapshotQualityVerdict(snapshotRef, validationRunRef, policy.qualityPolicyVersion(), status,
                blockers, errors, warnings, passed, failed, skipped, score, fingerprint);
    }

    private static boolean isPolicyDefinedNotApplicable(String checkCode) {
        return "exposure.binding".equals(checkCode);
    }
}
