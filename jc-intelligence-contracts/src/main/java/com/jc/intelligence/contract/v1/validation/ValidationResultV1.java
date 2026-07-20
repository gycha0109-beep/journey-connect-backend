package com.jc.intelligence.contract.v1.validation;

import com.jc.intelligence.contract.support.ImmutableCollections;
import java.util.List;

public record ValidationResultV1(List<ValidationErrorV1> errors) {
    public ValidationResultV1 {
        errors = ImmutableCollections.orderedCopy(errors, "errors");
    }

    public static ValidationResultV1 valid() {
        return new ValidationResultV1(List.of());
    }

    public static ValidationResultV1 invalid(
            IntelligenceValidationErrorCode code,
            String field,
            String message) {
        return new ValidationResultV1(List.of(new ValidationErrorV1(code, field, message)));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
