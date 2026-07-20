package com.jc.recommendation.p1.ranking;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.policy.P1DiversityPolicy;
import com.jc.recommendation.p1.policy.P1PolicyBundle;
import com.jc.recommendation.p1.policy.P1ScorePolicy;
import com.jc.recommendation.p1.profile.P1FeatureSignal;
import com.jc.recommendation.p1.support.P1Canonical;
import com.jc.recommendation.support.Utf16CodeUnitComparator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class P1RankingEngine {
    private static final double MILLIS_PER_DAY = 86_400_000.0d;

    public P1RankingResult rank(P1RankingInput input) {
        Objects.requireNonNull(input, "input");
        if (input.candidates().size() > 100) {
            throw new IllegalArgumentException("P1 candidate count exceeds 100");
        }
        Set<String> identities = new HashSet<>();
        for (P1CandidateInput candidate : input.candidates()) {
            if (!identities.add(candidate.identity())) {
                throw new IllegalArgumentException("duplicate candidate identity: " + candidate.identity());
            }
        }

        double maximumPopularity = input.candidates().stream()
                .mapToDouble(P1RankingEngine::rawPopularity)
                .max()
                .orElse(0.0d);
        P1PolicyBundle bundle = input.policySelection().policyBundle();
        Map<String, P1FeatureSignal> signals = new HashMap<>();
        for (P1FeatureSignal signal : input.profile().signals()) {
            signals.put(signal.featureId(), signal);
        }

        List<Scored> scored = new ArrayList<>();
        for (P1CandidateInput candidate : input.candidates()) {
            scored.add(score(candidate, input, bundle.scorePolicy(), signals, maximumPopularity));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(Comparator.comparingDouble(Scored::adjustedPopularity).reversed())
                .thenComparing(scoredCandidate -> scoredCandidate.candidate().entityType().wireValue(),
                        Utf16CodeUnitComparator.ASCENDING)
                .thenComparing(scoredCandidate -> scoredCandidate.candidate().entityId(),
                        Utf16CodeUnitComparator.ASCENDING));

        List<Scored> base = new ArrayList<>();
        for (int index = 0; index < scored.size(); index++) {
            base.add(scored.get(index).withBaseRank(index + 1));
        }
        List<Selected> diversified = diversify(base, bundle.diversityPolicy());
        List<P1RankedCandidate> ranked = new ArrayList<>();
        int boostedCount = 0;
        for (int index = 0; index < diversified.size(); index++) {
            Selected selected = diversified.get(index);
            Scored value = selected.scored();
            if (value.lowExposureBoost() > 0.0d) {
                boostedCount++;
            }
            ranked.add(new P1RankedCandidate(
                    index + 1,
                    value.baseRank(),
                    value.candidate().entityId(),
                    value.candidate().entityType(),
                    value.score(),
                    value.baseScore(),
                    value.contextScore(),
                    value.interestScore(),
                    value.freshnessScore(),
                    value.rawPopularity(),
                    value.adjustedPopularity(),
                    value.lowExposureBoost(),
                    value.candidate().recentExposureCount(),
                    selected.relaxations(),
                    value.candidate().diversityMetadata()));
        }

        String fingerprint = P1Canonical.sha256(canonicalResult(input, ranked));
        return new P1RankingResult(
                input.rankingSnapshotId(),
                input.userId(),
                input.contextId(),
                input.profile().fingerprint(),
                bundle.policyBundleVersion(),
                bundle.scorePolicy().policyVersion(),
                bundle.diversityPolicy().policyVersion(),
                bundle.retrievalPolicyVersion(),
                bundle.lowExposurePolicyVersion(),
                input.candidates().size(),
                ranked.size(),
                boostedCount,
                ranked,
                fingerprint);
    }

    private static Scored score(
            P1CandidateInput candidate,
            P1RankingInput input,
            P1ScorePolicy policy,
            Map<String, P1FeatureSignal> signals,
            double maximumPopularity) {
        double interest = interest(candidate.featureIds(), signals, policy.neutralInterestPrior());
        double ageDays = StrictMath.max(0.0d,
                Duration.between(candidate.publishedAt(), input.referenceTime()).toMillis() / MILLIS_PER_DAY);
        double freshness = StrictMath.pow(0.5d, ageDays / policy.freshnessHalfLifeDays());
        double popularityRawValue = rawPopularity(candidate);
        double popularityNormalized = maximumPopularity == 0.0d ? 0.0d : popularityRawValue / maximumPopularity;
        double adjustedPopularity = StrictMath.pow(popularityNormalized, policy.popularityCompressionExponent());
        double baseScore = candidate.contextScore() * policy.contextWeight()
                + interest * policy.interestWeight()
                + freshness * policy.freshnessWeight()
                + adjustedPopularity * policy.popularityWeight();
        double boost = candidate.recentExposureCount() >= policy.lowExposureThreshold()
                ? 0.0d
                : policy.lowExposureMaximumBoost()
                        * (policy.lowExposureThreshold() - candidate.recentExposureCount())
                        / policy.lowExposureThreshold();
        return new Scored(
                candidate,
                clamp(baseScore + boost),
                clamp(baseScore),
                candidate.contextScore(),
                interest,
                freshness,
                popularityNormalized,
                adjustedPopularity,
                boost,
                0);
    }

    private static double interest(
            List<String> featureIds,
            Map<String, P1FeatureSignal> signals,
            double neutralPrior) {
        if (featureIds.isEmpty() || signals.isEmpty()) {
            return neutralPrior;
        }
        double total = 0.0d;
        int matched = 0;
        for (String featureId : featureIds) {
            P1FeatureSignal signal = signals.get(featureId);
            if (signal == null) {
                continue;
            }
            double direction = signal.direction() == PreferenceKind.PREFER ? 1.0d : -1.0d;
            total += direction * signal.strength();
            matched++;
        }
        if (matched == 0) {
            return neutralPrior;
        }
        double signedAverage = total / matched;
        return clamp(0.5d + 0.5d * signedAverage);
    }

    private static double rawPopularity(P1CandidateInput candidate) {
        return StrictMath.log1p(candidate.viewCount())
                + 2.0d * StrictMath.log1p(candidate.likeCount())
                + 3.0d * StrictMath.log1p(candidate.bookmarkCount());
    }

    private static List<Selected> diversify(List<Scored> base, P1DiversityPolicy policy) {
        List<Scored> remaining = new ArrayList<>(base);
        List<Selected> selected = new ArrayList<>();
        while (!remaining.isEmpty()) {
            int targetRank = selected.size() + 1;
            int forcedIndex = forcedDemotionIndex(remaining, targetRank, policy.maxDemotionDistance());
            EnumSet<DiversityDimension> relaxed = EnumSet.noneOf(DiversityDimension.class);
            int selectedIndex = findCandidate(
                    remaining, selected, policy, relaxed, targetRank, forcedIndex);
            while (selectedIndex < 0 && relaxed.size() < policy.relaxationOrder().size()) {
                relaxed.add(policy.relaxationOrder().get(relaxed.size()));
                selectedIndex = findCandidate(
                        remaining, selected, policy, relaxed, targetRank, forcedIndex);
            }
            if (selectedIndex < 0) {
                throw new IllegalStateException("P1 diversity could not select an eligible candidate");
            }
            Scored value = remaining.remove(selectedIndex);
            selected.add(new Selected(value, List.copyOf(relaxed)));
        }
        return List.copyOf(selected);
    }

    private static int forcedDemotionIndex(
            List<Scored> remaining,
            int targetRank,
            int maxDemotionDistance) {
        for (int index = 0; index < remaining.size(); index++) {
            Scored candidate = remaining.get(index);
            if (targetRank >= candidate.baseRank() + maxDemotionDistance) {
                return index;
            }
        }
        return -1;
    }

    private static int findCandidate(
            List<Scored> remaining,
            List<Selected> selected,
            P1DiversityPolicy policy,
            Set<DiversityDimension> relaxed,
            int targetRank,
            int forcedIndex) {
        int windowStart = StrictMath.max(0, selected.size() - policy.exposureWindowSize() + 1);
        List<Selected> window = selected.subList(windowStart, selected.size());
        if (forcedIndex >= 0) {
            Scored forced = remaining.get(forcedIndex);
            return fits(forced.candidate().diversityMetadata(), window, policy, relaxed)
                    ? forcedIndex
                    : -1;
        }
        int latestPromotableBaseRank = targetRank + policy.maxPromotionDistance();
        for (int index = 0; index < remaining.size(); index++) {
            Scored candidate = remaining.get(index);
            if (candidate.baseRank() > latestPromotableBaseRank) {
                break;
            }
            if (fits(candidate.candidate().diversityMetadata(), window, policy, relaxed)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean fits(
            DiversityCandidateMetadata candidate,
            List<Selected> selected,
            P1DiversityPolicy policy,
            Set<DiversityDimension> relaxed) {
        for (DiversityDimension dimension : DiversityDimension.values()) {
            if (relaxed.contains(dimension)) {
                continue;
            }
            String value = dimension(candidate, dimension);
            if (value == null) {
                continue;
            }
            long count = selected.stream()
                    .map(item -> dimension(item.scored().candidate().diversityMetadata(), dimension))
                    .filter(value::equals)
                    .count();
            if (count >= policy.exposureCaps().get(dimension)) {
                return false;
            }
        }
        return true;
    }

    private static String dimension(DiversityCandidateMetadata metadata, DiversityDimension dimension) {
        return switch (dimension) {
            case DUPLICATE_GROUP -> metadata.duplicateGroupId();
            case AUTHOR -> metadata.authorId();
            case REGION -> metadata.primaryRegionFeatureId();
            case THEME -> metadata.primaryThemeFeatureId();
        };
    }

    private static Map<String, Object> canonicalResult(
            P1RankingInput input,
            List<P1RankedCandidate> candidates) {
        Map<String, Object> result = new TreeMap<>();
        result.put("domain", "journey-connect:p1-ranking:v1");
        result.put("userId", input.userId());
        result.put("contextId", input.contextId());
        result.put("referenceTime", input.referenceTime().toString());
        result.put("profileFingerprint", input.profile().fingerprint());
        result.put("policyBundleVersion", input.policySelection().policyBundle().policyBundleVersion());
        result.put("candidates", candidates.stream().map(P1RankingEngine::canonicalCandidate).toList());
        return result;
    }

    private static Map<String, Object> canonicalCandidate(P1RankedCandidate candidate) {
        Map<String, Object> result = new TreeMap<>();
        result.put("absoluteRank", candidate.absoluteRank());
        result.put("baseRank", candidate.baseRank());
        result.put("entityId", candidate.entityId());
        result.put("entityType", candidate.entityType().wireValue());
        result.put("score", candidate.score());
        result.put("baseScore", candidate.baseScore());
        result.put("contextScore", candidate.contextScore());
        result.put("interestScore", candidate.interestScore());
        result.put("freshnessScore", candidate.freshnessScore());
        result.put("rawPopularityScore", candidate.rawPopularityScore());
        result.put("adjustedPopularityScore", candidate.adjustedPopularityScore());
        result.put("lowExposureBoost", candidate.lowExposureBoost());
        result.put("recentExposureCount", candidate.recentExposureCount());
        result.put("appliedRelaxations", candidate.appliedRelaxations().stream()
                .map(DiversityDimension::wireValue)
                .toList());
        Map<String, Object> diversity = new TreeMap<>();
        diversity.put("authorId", candidate.diversityMetadata().authorId());
        diversity.put("primaryRegionFeatureId", candidate.diversityMetadata().primaryRegionFeatureId());
        diversity.put("primaryThemeFeatureId", candidate.diversityMetadata().primaryThemeFeatureId());
        diversity.put("duplicateGroupId", candidate.diversityMetadata().duplicateGroupId());
        result.put("diversityMetadata", diversity);
        return result;
    }

    private static double clamp(double value) {
        return StrictMath.max(0.0d, StrictMath.min(1.0d, value));
    }

    private record Scored(
            P1CandidateInput candidate,
            double score,
            double baseScore,
            double contextScore,
            double interestScore,
            double freshnessScore,
            double rawPopularity,
            double adjustedPopularity,
            double lowExposureBoost,
            int baseRank) {

        private Scored withBaseRank(int rank) {
            return new Scored(
                    candidate,
                    score,
                    baseScore,
                    contextScore,
                    interestScore,
                    freshnessScore,
                    rawPopularity,
                    adjustedPopularity,
                    lowExposureBoost,
                    rank);
        }
    }

    private record Selected(Scored scored, List<DiversityDimension> relaxations) {
    }
}
