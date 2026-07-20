package com.jc.intelligence.integration.search.v1;

import java.time.Duration;

public record SearchShadowComparisonMetricsV1(
        int legacyCount,
        int runtimeCount,
        int intersectionCount,
        int legacyOnlyCount,
        int runtimeOnlyCount,
        int topKOverlapCount,
        double topKOverlapRatio,
        int sameOrderPrefixLength,
        int duplicateCount,
        Duration comparisonDuration,
        Duration runtimeDuration) {
    public SearchShadowComparisonMetricsV1 {
        for (int value : new int[]{legacyCount, runtimeCount, intersectionCount, legacyOnlyCount, runtimeOnlyCount,
                topKOverlapCount, sameOrderPrefixLength, duplicateCount}) {
            if (value < 0) throw new IllegalArgumentException("comparison counts must be nonnegative");
        }
        if (!Double.isFinite(topKOverlapRatio) || topKOverlapRatio < 0.0d || topKOverlapRatio > 1.0d) {
            throw new IllegalArgumentException("topKOverlapRatio must be finite and within 0..1");
        }
        if (intersectionCount > Math.min(legacyCount, runtimeCount)
                || legacyOnlyCount > legacyCount || runtimeOnlyCount > runtimeCount
                || sameOrderPrefixLength > Math.min(legacyCount, runtimeCount)
                || duplicateCount > legacyCount + runtimeCount) {
            throw new IllegalArgumentException("comparison metrics are inconsistent");
        }
        if (comparisonDuration == null || comparisonDuration.isNegative()
                || runtimeDuration == null || runtimeDuration.isNegative()) {
            throw new IllegalArgumentException("durations must be nonnegative");
        }
    }
}
