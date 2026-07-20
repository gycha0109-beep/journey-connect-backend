package com.jc.intelligence.contract.v1.validation;

import java.time.Instant;

public final class TimeValidatorV1 {
    private TimeValidatorV1() {
    }

    public static ValidationResultV1 validateRange(
            Instant start,
            Instant end,
            String startField,
            String endField) {
        try {
            ContractChecks.requireOrdered(start, end, startField, endField);
            return ValidationResultV1.valid();
        } catch (IntelligenceContractValidationException exception) {
            return ValidationResultV1.invalid(exception.errorCode(), endField, exception.getMessage());
        } catch (NullPointerException exception) {
            return ValidationResultV1.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_TIME_RANGE_INVALID,
                    start == null ? startField : endField,
                    "Instant is required");
        }
    }
}
