package com.jc.recommendation.policy;

import java.time.Instant;

public final class CandidateLimitPolicies {
    public static final int MAX_CANDIDATES_TO_SCORE = 100;
    public static final int DEFAULT_RESULT_LIMIT = 20;
    public static final int HARD_RESULT_LIMIT = 30;

    public static final CandidateLimitPolicy V1 = new CandidateLimitPolicy(
            "candidate-limit-v1",
            Instant.parse("2026-07-01T00:00:00Z"),
            MAX_CANDIDATES_TO_SCORE,
            DEFAULT_RESULT_LIMIT,
            HARD_RESULT_LIMIT
    );

    private CandidateLimitPolicies() {
    }
}
