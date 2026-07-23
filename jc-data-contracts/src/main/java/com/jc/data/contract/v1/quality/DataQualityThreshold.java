package com.jc.data.contract.v1.quality;

import java.math.BigDecimal;
import java.util.Objects;

public record DataQualityThreshold(
        String metricName,
        BigDecimal threshold,
        DataQualityThresholdOperator operator) {
    public DataQualityThreshold {
        metricName = QualityContractSupport.token(metricName, "metricName", 96);
        threshold = QualityContractSupport.decimal(threshold, "threshold");
        Objects.requireNonNull(operator, "operator");
    }

    public boolean test(BigDecimal value) {
        int comparison = value.compareTo(threshold);
        return switch (operator) {
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
        };
    }
}
