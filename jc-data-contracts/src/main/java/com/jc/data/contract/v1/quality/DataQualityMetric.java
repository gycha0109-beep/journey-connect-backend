package com.jc.data.contract.v1.quality;

import java.math.BigDecimal;
import java.util.Objects;

public record DataQualityMetric(
        String metricName,
        long numerator,
        long denominator,
        BigDecimal metricValue,
        String metricUnit,
        DataQualityThreshold threshold,
        DataQualityMetricStatus thresholdResult,
        String metricVersion,
        String metricFingerprint) {
    public DataQualityMetric {
        metricName = QualityContractSupport.token(metricName, "metricName", 96);
        if (numerator < 0 || denominator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("ratio metric requires 0 <= numerator <= denominator");
        }
        if (metricValue != null) metricValue = QualityContractSupport.decimal(metricValue, "metricValue");
        metricUnit = QualityContractSupport.token(metricUnit, "metricUnit", 32);
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(thresholdResult, "thresholdResult");
        metricVersion = QualityContractSupport.version(metricVersion, "metricVersion");
        metricFingerprint = QualityContractSupport.fingerprint(metricFingerprint, "metricFingerprint");
        if (denominator == 0 && thresholdResult == DataQualityMetricStatus.PASS) {
            throw new IllegalArgumentException("zero denominator cannot be PASS");
        }
    }
}
