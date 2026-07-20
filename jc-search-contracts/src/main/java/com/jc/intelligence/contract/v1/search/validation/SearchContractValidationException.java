package com.jc.intelligence.contract.v1.search.validation;

public final class SearchContractValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final SearchValidationErrorCode errorCode;

    public SearchContractValidationException(SearchValidationErrorCode errorCode, String message) {
        super(message);
        this.errorCode = java.util.Objects.requireNonNull(errorCode, "errorCode");
    }

    public SearchValidationErrorCode errorCode() { return errorCode; }
}
