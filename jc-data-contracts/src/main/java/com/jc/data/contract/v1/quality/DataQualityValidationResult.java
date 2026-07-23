package com.jc.data.contract.v1.quality;

import java.util.List;
import java.util.Objects;

public record DataQualityValidationResult(
        DataQualityValidationRun run,
        List<DataQualityCheckResult> checks,
        List<DataQualityMetric> metrics,
        List<DataQualityAnomaly> anomalies,
        List<LateArrivalObservation> lateArrivals,
        RebuildComparison rebuildComparison,
        SnapshotQualityVerdict verdict) {
    public DataQualityValidationResult {
        Objects.requireNonNull(run, "run");
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics"));
        anomalies = List.copyOf(Objects.requireNonNull(anomalies, "anomalies"));
        lateArrivals = List.copyOf(Objects.requireNonNull(lateArrivals, "lateArrivals"));
        Objects.requireNonNull(rebuildComparison, "rebuildComparison");
        Objects.requireNonNull(verdict, "verdict");
    }
}
