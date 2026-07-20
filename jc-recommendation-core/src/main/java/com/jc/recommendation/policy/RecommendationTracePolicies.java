package com.jc.recommendation.policy;

import java.time.Instant;

public final class RecommendationTracePolicies {
    public static final RecommendationTracePolicy V1 = new RecommendationTracePolicy(
            "recommendation-trace-v1", Instant.parse("2026-07-01T00:00:00Z"),
            "recommendation-exposure-v1", "ranking-v3", "ranking-cursor-v3",
            CandidateLimitPolicies.HARD_RESULT_LIMIT, "sha256_canonical_json_v1",
            "same_key_same_payload_dedupe_conflict_error", "caller_supplied_strict_utc",
            "page_successfully_served", "explicit_boolean_flag", "forbidden", "forbidden", "forbidden"
    );
    private RecommendationTracePolicies() {}
}
