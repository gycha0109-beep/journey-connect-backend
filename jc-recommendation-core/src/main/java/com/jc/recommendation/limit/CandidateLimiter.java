package com.jc.recommendation.limit;

import com.jc.recommendation.policy.CandidateLimitPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CandidateLimiter {
    private CandidateLimiter() {
    }

    public static <T> List<T> limitCandidates(List<T> candidates, CandidateLimitPolicy policy) {
        return limitCandidates(candidates, policy, policy.maxCandidatesToScore());
    }

    public static <T> List<T> limitCandidates(
            List<T> candidates,
            CandidateLimitPolicy policy,
            int maxCandidates
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(policy, "policy");
        if (maxCandidates < 1) {
            throw new IllegalArgumentException("maxCandidates must be an integer greater than or equal to 1");
        }
        int effectiveLimit = Math.min(maxCandidates, policy.maxCandidatesToScore());
        int endIndex = Math.min(candidates.size(), effectiveLimit);
        return Collections.unmodifiableList(new ArrayList<>(candidates.subList(0, endIndex)));
    }
}
