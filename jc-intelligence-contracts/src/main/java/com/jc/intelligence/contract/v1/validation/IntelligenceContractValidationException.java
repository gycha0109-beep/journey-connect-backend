package com.jc.intelligence.contract.v1.validation;

public final class IntelligenceContractValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final IntelligenceValidationErrorCode errorCode;

    public IntelligenceContractValidationException(
            IntelligenceValidationErrorCode errorCode,
            String message) {
        super(message);
        this.errorCode = java.util.Objects.requireNonNull(errorCode, "errorCode");
    }

    public IntelligenceValidationErrorCode errorCode() {
        return errorCode;
    }
}
