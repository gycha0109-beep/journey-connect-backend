package com.jc.intelligence.contract.v1.validation;

public record ValidationErrorV1(IntelligenceValidationErrorCode code, String field, String message) {
    public ValidationErrorV1 {
        java.util.Objects.requireNonNull(code, "code");
        field = field == null ? "" : field;
        message = message == null ? "" : message;
    }
}
