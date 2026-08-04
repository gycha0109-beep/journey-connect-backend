package com.jc.backend.intelligence.search;

import java.time.Duration;

public record SearchExposureValidationPolicy(
        String visibilityRuleVersion,
        int minimumVisibleRatioBasisPoints,
        long minimumDwellMilliseconds,
        long maximumDwellMilliseconds,
        Duration maximumFutureSkew,
        Duration contextClockSkew) {

    public SearchExposureValidationPolicy {
        if (visibilityRuleVersion == null || visibilityRuleVersion.isBlank()) {
            throw new IllegalArgumentException("visibilityRuleVersion is required");
        }
        if (minimumVisibleRatioBasisPoints < 0
                || minimumVisibleRatioBasisPoints > SearchExposureContract.MAX_VISIBLE_RATIO_BASIS_POINTS) {
            throw new IllegalArgumentException("minimumVisibleRatioBasisPoints is invalid");
        }
        if (minimumDwellMilliseconds < 0
                || maximumDwellMilliseconds < minimumDwellMilliseconds) {
            throw new IllegalArgumentException("dwell limits are invalid");
        }
        if (maximumFutureSkew == null
                || maximumFutureSkew.isNegative()
                || contextClockSkew == null
                || contextClockSkew.isNegative()) {
            throw new IllegalArgumentException("clock skew limits are invalid");
        }
    }

    public static SearchExposureValidationPolicy candidateV1() {
        return new SearchExposureValidationPolicy(
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                SearchExposureContract.MIN_VISIBLE_RATIO_BASIS_POINTS,
                SearchExposureContract.MIN_DWELL_MILLISECONDS,
                SearchExposureContract.MAX_DWELL_MILLISECONDS,
                SearchExposureContract.MAX_FUTURE_SKEW,
                SearchExposureContract.MAX_CONTEXT_CLOCK_SKEW);
    }
}
