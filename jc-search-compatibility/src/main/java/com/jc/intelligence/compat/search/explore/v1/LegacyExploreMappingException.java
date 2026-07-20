package com.jc.intelligence.compat.search.explore.v1;

final class LegacyExploreMappingException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final LegacyExploreCompatibilityStatus status;
    private final transient LegacyExploreMappingFailure failure;

    LegacyExploreMappingException(LegacyExploreCompatibilityStatus status, LegacyExploreMappingFailure failure) {
        super(failure.message());
        this.status = status;
        this.failure = failure;
    }

    LegacyExploreCompatibilityStatus status() { return status; }
    LegacyExploreMappingFailure failure() { return failure; }
}
