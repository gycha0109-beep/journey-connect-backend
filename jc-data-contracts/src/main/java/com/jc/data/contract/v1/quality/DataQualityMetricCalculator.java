package com.jc.data.contract.v1.quality;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DataQualityMetricCalculator {
    public DataQualityMetric calculate(String metricName, long numerator, long denominator,
            DataQualityPolicy policy) {
        DataQualityThreshold threshold = policy.thresholds().get(metricName);
        if (threshold == null) throw new IllegalArgumentException("missing threshold: " + metricName);
        if (numerator < 0 || denominator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("ratio metric requires 0 <= numerator <= denominator");
        }
        if (denominator == 0) {
            DataQualityMetricStatus status = switch (policy.zeroDenominatorPolicy()) {
                case NOT_APPLICABLE -> DataQualityMetricStatus.NOT_APPLICABLE;
                case UNDEFINED -> DataQualityMetricStatus.UNDEFINED;
                case POLICY_DEFINED_ZERO_CASE -> DataQualityMetricStatus.POLICY_DEFINED_ZERO_CASE;
            };
            String fp = DataQualityFingerprints.metric(metricName, numerator, denominator, "", "ratio",
                    status, threshold);
            return new DataQualityMetric(metricName, numerator, denominator, null, "ratio", threshold,
                    status, "data-quality-metric-v1", fp);
        }
        BigDecimal value = BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 12,
                RoundingMode.HALF_UP).stripTrailingZeros();
        DataQualityMetricStatus status = threshold.test(value)
                ? DataQualityMetricStatus.PASS : DataQualityMetricStatus.FAIL;
        String fp = DataQualityFingerprints.metric(metricName, numerator, denominator,
                value.toPlainString(), "ratio", status, threshold);
        return new DataQualityMetric(metricName, numerator, denominator, value, "ratio", threshold,
                status, "data-quality-metric-v1", fp);
    }
}
