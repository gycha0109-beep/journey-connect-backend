package com.jc.data.contract.v1.validation;

import java.util.List;

public record ValidationResult<T>(T value, List<ValidationError> errors) {
    public ValidationResult {
        errors = List.copyOf(errors);
        if (errors.isEmpty() && value == null) {
            throw new IllegalArgumentException("valid result requires a value");
        }
        if (!errors.isEmpty() && value != null) {
            throw new IllegalArgumentException("invalid result cannot expose a value");
        }
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public static <T> ValidationResult<T> valid(T value) {
        return new ValidationResult<>(value, List.of());
    }

    public static <T> ValidationResult<T> invalid(List<ValidationError> errors) {
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("invalid result requires errors");
        }
        return new ValidationResult<>(null, errors);
    }
}
