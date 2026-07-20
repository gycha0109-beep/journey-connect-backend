package com.jc.intelligence.readiness.search.v1;

import java.util.Objects;

public record SearchDisabledModeObservationV1<T>(
        T responseBefore,
        T responseAfter,
        int executorSubmissions,
        int runtimeInvocations,
        int comparisonInvocations,
        int loggingInvocations,
        boolean itemOrderPreserved,
        boolean paginationPreserved,
        boolean exceptionSemanticsPreserved,
        boolean serializationPreserved) {
    public SearchDisabledModeObservationV1 {
        Objects.requireNonNull(responseBefore, "responseBefore");
        Objects.requireNonNull(responseAfter, "responseAfter");
        if (executorSubmissions < 0 || runtimeInvocations < 0 || comparisonInvocations < 0 || loggingInvocations < 0) {
            throw new IllegalArgumentException("invocation counts cannot be negative");
        }
    }
}
