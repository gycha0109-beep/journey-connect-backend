package com.jc.intelligence.readiness.search.v1;

import java.util.ArrayList;
import java.util.List;

public record SearchControlledHookProposalV1(
        String endpointId,
        String recommendedBoundary,
        List<String> protectedSourceFiles,
        boolean sourceApplied,
        boolean hookReturnIgnored,
        boolean defaultNoOp,
        boolean defaultDisabled,
        boolean defaultSampleZero,
        boolean exceptionIsolated,
        boolean boundedExecutorRequired,
        boolean legacyResponseAuthority) {
    public SearchControlledHookProposalV1 {
        endpointId = safe(endpointId, "endpointId");
        recommendedBoundary = safe(recommendedBoundary, "recommendedBoundary");
        if (protectedSourceFiles == null || protectedSourceFiles.isEmpty()) throw new IllegalArgumentException("protectedSourceFiles are required");
        protectedSourceFiles = List.copyOf(new ArrayList<>(protectedSourceFiles));
        if (protectedSourceFiles.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("invalid protected source file");
        if (sourceApplied) throw new IllegalArgumentException("IP-8 proposal must not be applied");
        if (!hookReturnIgnored || !defaultNoOp || !defaultDisabled || !defaultSampleZero || !exceptionIsolated
                || !boundedExecutorRequired || !legacyResponseAuthority) {
            throw new IllegalArgumentException("controlled hook safety invariants are required");
        }
    }
    private static String safe(String value, String field) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_./:-]{0,199}")) throw new IllegalArgumentException(field + " must be safe");
        return value;
    }
}
