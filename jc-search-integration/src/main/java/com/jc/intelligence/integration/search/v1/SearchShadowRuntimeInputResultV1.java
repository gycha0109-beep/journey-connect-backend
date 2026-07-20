package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;

public record SearchShadowRuntimeInputResultV1(
        SearchShadowRuntimeInputStatus status,
        SearchRuntimeExecutionRequestV1 executionRequest,
        String runtimeInputFingerprint,
        String safeReason) {
    public SearchShadowRuntimeInputResultV1 {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (status == SearchShadowRuntimeInputStatus.AVAILABLE) {
            if (executionRequest == null) throw new IllegalArgumentException("available input requires executionRequest");
            runtimeInputFingerprint = requireHash(runtimeInputFingerprint);
            if (!runtimeInputFingerprint.equals(SearchShadowFingerprintV1.runtimeInput(executionRequest))) {
                throw new IllegalArgumentException("runtimeInputFingerprint must bind the execution request");
            }
            if (safeReason != null) throw new IllegalArgumentException("available input must not carry failure reason");
        } else {
            if (executionRequest != null || runtimeInputFingerprint != null) {
                throw new IllegalArgumentException("non-available input cannot carry execution request or fingerprint");
            }
            safeReason = requireSafeReason(safeReason);
        }
    }

    public static SearchShadowRuntimeInputResultV1 available(
            SearchRuntimeExecutionRequestV1 request, String fingerprint) {
        return new SearchShadowRuntimeInputResultV1(SearchShadowRuntimeInputStatus.AVAILABLE, request, fingerprint, null);
    }
    public static SearchShadowRuntimeInputResultV1 unavailable(String reason) {
        return new SearchShadowRuntimeInputResultV1(SearchShadowRuntimeInputStatus.UNAVAILABLE, null, null, reason);
    }
    public static SearchShadowRuntimeInputResultV1 unsupported(String reason) {
        return new SearchShadowRuntimeInputResultV1(SearchShadowRuntimeInputStatus.UNSUPPORTED, null, null, reason);
    }
    public static SearchShadowRuntimeInputResultV1 invalid(String reason) {
        return new SearchShadowRuntimeInputResultV1(SearchShadowRuntimeInputStatus.INVALID, null, null, reason);
    }

    private static String requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("runtimeInputFingerprint must be lowercase SHA-256");
        }
        return value;
    }
    private static String requireSafeReason(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 160) {
            throw new IllegalArgumentException("safeReason must be trimmed nonblank text up to 160 characters");
        }
        return value;
    }
}
