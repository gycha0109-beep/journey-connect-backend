package com.jc.intelligence.compat.search.explore.v1;

public record LegacyExploreMappingFailure(
        LegacyExploreMappingFailureCode failureCode, String field, Integer itemIndex, String message) {
    public LegacyExploreMappingFailure {
        if (failureCode == null) throw new IllegalArgumentException("failureCode is required");
        if (itemIndex != null && itemIndex < 0) throw new IllegalArgumentException("itemIndex must be non-negative");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
    }
}
