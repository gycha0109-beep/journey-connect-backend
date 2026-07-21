package com.jc.data.contract.v1.validation;

import java.util.Objects;

public record ValidationError(DataValidationErrorCode code, String field, String detail) {
    public ValidationError {
        Objects.requireNonNull(code, "code");
        field = field == null ? "" : field;
        detail = detail == null ? "" : detail;
    }
}
