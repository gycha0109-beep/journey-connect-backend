package com.jc.recommendation.p1.evaluation;

import com.jc.recommendation.p1.ranking.P1RankedCandidate;
import com.jc.recommendation.p1.support.P1Canonical;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class P1RankingComparisonEngine {
    public P1RankingComparison compare(
            String baselinePolicyVersion,
            List<String> baselineEntityIds,
            String treatmentPolicyVersion,
            List<P1RankedCandidate> treatmentCandidates,
            int cutoff) {
        Objects.requireNonNull(baselinePolicyVersion, "baselinePolicyVersion");
        Objects.requireNonNull(baselineEntityIds, "baselineEntityIds");
        Objects.requireNonNull(treatmentPolicyVersion, "treatmentPolicyVersion");
        Objects.requireNonNull(treatmentCandidates, "treatmentCandidates");
        if (cutoff < 1 || cutoff > 100) {
            throw new IllegalArgumentException("cutoff must be within [1,100]");
        }
        int baselineCount = StrictMath.min(cutoff, baselineEntityIds.size());
        int treatmentCount = StrictMath.min(cutoff, treatmentCandidates.size());
        List<String> baseline = baselineEntityIds.subList(0, baselineCount);
        List<P1RankedCandidate> treatment = treatmentCandidates.subList(0, treatmentCount);

        Map<String, Integer> baselineRanks = new HashMap<>();
        for (int index = 0; index < baseline.size(); index++) {
            String entityId = baseline.get(index);
            if (baselineRanks.putIfAbsent(entityId, index + 1) != null) {
                throw new IllegalArgumentException("baseline contains duplicate entity ID: " + entityId);
            }
        }

        int overlap = 0;
        double displacement = 0.0d;
        int lowExposure = 0;
        double popularityTotal = 0.0d;
        Set<String> treatmentIds = new HashSet<>();
        Map<String, Integer> authorCounts = new HashMap<>();
        Map<String, Integer> regionCounts = new HashMap<>();
        Set<String> themes = new HashSet<>();
        for (P1RankedCandidate candidate : treatment) {
            if (!treatmentIds.add(candidate.entityId())) {
                throw new IllegalArgumentException("treatment contains duplicate entity ID: " + candidate.entityId());
            }
            Integer baselineRank = baselineRanks.get(candidate.entityId());
            if (baselineRank != null) {
                overlap++;
                displacement += StrictMath.abs(baselineRank - candidate.absoluteRank());
            }
            if (candidate.lowExposureBoost() > 0.0d) {
                lowExposure++;
            }
            popularityTotal += candidate.adjustedPopularityScore();
            increment(authorCounts, candidate.diversityMetadata().authorId());
            increment(regionCounts, candidate.diversityMetadata().primaryRegionFeatureId());
            String theme = candidate.diversityMetadata().primaryThemeFeatureId();
            if (theme != null) {
                themes.add(theme);
            }
        }

        double overlapRate = StrictMath.max(baselineCount, treatmentCount) == 0
                ? 1.0d
                : overlap / (double) StrictMath.max(baselineCount, treatmentCount);
        double meanDisplacement = overlap == 0 ? 0.0d : displacement / overlap;
        double lowExposureShare = treatmentCount == 0 ? 0.0d : lowExposure / (double) treatmentCount;
        double topAuthorShare = shareOfLargest(authorCounts, treatmentCount);
        double topRegionShare = shareOfLargest(regionCounts, treatmentCount);
        double meanPopularity = treatmentCount == 0 ? 0.0d : popularityTotal / treatmentCount;

        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("domain", "journey-connect:p1-comparison:v1");
        canonical.put("baselinePolicyVersion", baselinePolicyVersion);
        canonical.put("treatmentPolicyVersion", treatmentPolicyVersion);
        canonical.put("cutoff", cutoff);
        canonical.put("baseline", baseline);
        canonical.put("treatment", treatment.stream().map(P1RankedCandidate::entityId).toList());
        canonical.put("metrics", List.of(
                overlap,
                overlapRate,
                meanDisplacement,
                authorCounts.size(),
                regionCounts.size(),
                themes.size(),
                lowExposureShare,
                topAuthorShare,
                topRegionShare,
                meanPopularity));
        return new P1RankingComparison(
                baselinePolicyVersion,
                treatmentPolicyVersion,
                cutoff,
                baselineCount,
                treatmentCount,
                overlap,
                overlapRate,
                meanDisplacement,
                authorCounts.size(),
                regionCounts.size(),
                themes.size(),
                lowExposureShare,
                topAuthorShare,
                topRegionShare,
                meanPopularity,
                P1Canonical.sha256(canonical));
    }

    private static void increment(Map<String, Integer> counts, String value) {
        if (value != null) {
            counts.merge(value, 1, Integer::sum);
        }
    }

    private static double shareOfLargest(Map<String, Integer> counts, int total) {
        if (total == 0 || counts.isEmpty()) {
            return 0.0d;
        }
        int maximum = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return maximum / (double) total;
    }
}
