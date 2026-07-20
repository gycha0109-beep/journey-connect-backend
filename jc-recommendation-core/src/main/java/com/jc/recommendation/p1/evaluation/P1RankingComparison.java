package com.jc.recommendation.p1.evaluation;

import java.util.Objects;

public record P1RankingComparison(
        String baselinePolicyVersion,
        String treatmentPolicyVersion,
        int cutoff,
        int baselineCount,
        int treatmentCount,
        int overlapCount,
        double overlapRate,
        double meanAbsoluteRankDisplacement,
        int treatmentUniqueAuthorCount,
        int treatmentUniqueRegionCount,
        int treatmentUniqueThemeCount,
        double treatmentLowExposureShare,
        double treatmentTopAuthorShare,
        double treatmentTopRegionShare,
        double treatmentMeanAdjustedPopularity,
        String fingerprint) {

    public P1RankingComparison {
        Objects.requireNonNull(baselinePolicyVersion, "baselinePolicyVersion");
        Objects.requireNonNull(treatmentPolicyVersion, "treatmentPolicyVersion");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (cutoff < 1 || baselineCount < 0 || treatmentCount < 0 || overlapCount < 0
                || baselineCount > cutoff || treatmentCount > cutoff
                || overlapCount > baselineCount || overlapCount > treatmentCount
                || treatmentUniqueAuthorCount < 0 || treatmentUniqueAuthorCount > treatmentCount
                || treatmentUniqueRegionCount < 0 || treatmentUniqueRegionCount > treatmentCount
                || treatmentUniqueThemeCount < 0 || treatmentUniqueThemeCount > treatmentCount) {
            throw new IllegalArgumentException("comparison counts are invalid");
        }
        validateUnit(overlapRate, "overlapRate");
        validateNonnegative(meanAbsoluteRankDisplacement, "meanAbsoluteRankDisplacement");
        validateUnit(treatmentLowExposureShare, "treatmentLowExposureShare");
        validateUnit(treatmentTopAuthorShare, "treatmentTopAuthorShare");
        validateUnit(treatmentTopRegionShare, "treatmentTopRegionShare");
        validateUnit(treatmentMeanAdjustedPopularity, "treatmentMeanAdjustedPopularity");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be lowercase SHA-256 hex");
        }
    }

    private static void validateUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1]");
        }
    }

    private static void validateNonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
