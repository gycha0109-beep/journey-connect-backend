package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult;

public record SearchShadowDispatchReceiptV1<T>(
        T legacyResponse,
        SearchShadowDispatchStatus status,
        SearchShadowSamplingDecisionV1 samplingDecision,
        SearchShadowCircuitDecisionV1 circuitDecision,
        SearchShadowIntegrationResult<T> integrationResult,
        SearchShadowComparisonLogResultV1 logResult,
        String safeCode,
        SearchShadowWiringAuthorityV1 authority) {
    public SearchShadowDispatchReceiptV1 {
        if (legacyResponse == null || status == null || authority == null) throw new IllegalArgumentException("receipt fields are required");
        if (safeCode != null && !safeCode.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("safeCode must be lowercase_snake_case");
        if (!authority.equals(SearchShadowWiringAuthorityV1.legacyOnly())) throw new IllegalArgumentException("receipt must preserve legacy-only authority");
        if (integrationResult != null && integrationResult.legacyResponse() != legacyResponse) {
            throw new IllegalArgumentException("integration result must preserve legacy response identity");
        }
        switch (status) {
            case DISABLED, PROFILE_BLOCKED -> {
                if (samplingDecision != null || circuitDecision != null || integrationResult != null || logResult != null) {
                    throw new IllegalArgumentException("inactive receipt cannot fabricate shadow execution fields");
                }
            }
            case NOT_SAMPLED -> {
                if (samplingDecision == null || samplingDecision.included() || circuitDecision != null || integrationResult != null) {
                    throw new IllegalArgumentException("not-sampled receipt is inconsistent");
                }
            }
            case CIRCUIT_OPEN -> {
                if (samplingDecision == null || !samplingDecision.included() || circuitDecision == null || circuitDecision.permitted()
                        || integrationResult != null) throw new IllegalArgumentException("circuit-open receipt is inconsistent");
            }
            case REJECTED, QUEUE_FULL, EXECUTOR_UNAVAILABLE, TIMED_OUT, CANCELLED, FAILED -> {
                if (samplingDecision == null || !samplingDecision.included() || circuitDecision == null || !circuitDecision.permitted()) {
                    throw new IllegalArgumentException("executor/failure receipt requires accepted activation and sampling");
                }
            }
            case INPUT_UNAVAILABLE, INPUT_UNSUPPORTED, INVALID_INPUT, COMPLETED, COMPARISON_FAILED, LOGGING_FAILED -> {
                if (samplingDecision == null || !samplingDecision.included() || circuitDecision == null || !circuitDecision.permitted()
                        || integrationResult == null) throw new IllegalArgumentException("active receipt requires integration result");
            }
            case SUBMITTED -> {
                if (samplingDecision == null || !samplingDecision.included()
                        || circuitDecision == null || !circuitDecision.permitted()
                        || integrationResult != null || logResult != null) {
                    throw new IllegalArgumentException("submitted receipt requires accepted sampling/circuit without completed result");
                }
            }
        }
    }
}
