package com.jc.backend.intelligence.search;

import com.jc.backend.intelligence.search.RecommendationSearchProfileSource.SearchInterestProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SearchRankingPolicy {

    public static final String POLICY_VERSION = "search-ranking-policy-v1";
    private static final int DIVERSITY_WINDOW = 20;

    public List<RankedSearchCandidate> rank(
            List<SearchCandidate> candidates,
            SearchInterestProfile profile,
            Instant referenceTime) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(referenceTime, "referenceTime");

        List<ScoredSearchCandidate> remaining = candidates.stream()
                .map(candidate -> score(candidate, profile, referenceTime))
                .sorted(baseComparator())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        List<RankedSearchCandidate> ranked = new ArrayList<>(remaining.size());
        Map<Long, Integer> authorCounts = new HashMap<>();
        Map<String, Integer> tagCounts = new HashMap<>();

        while (!remaining.isEmpty()) {
            int window = Math.min(DIVERSITY_WINDOW, remaining.size());
            int bestIndex = 0;
            CandidateWithAdjustment best = adjusted(
                    remaining.get(0), authorCounts, tagCounts);
            for (int index = 1; index < window; index++) {
                CandidateWithAdjustment current = adjusted(
                        remaining.get(index), authorCounts, tagCounts);
                if (adjustedComparator().compare(current, best) < 0) {
                    best = current;
                    bestIndex = index;
                }
            }
            remaining.remove(bestIndex);
            SearchCandidate candidate = best.scored().candidate();
            ranked.add(new RankedSearchCandidate(
                    candidate,
                    best.scored().searchRelevance(),
                    best.scored().interestMatch(),
                    best.scored().popularity(),
                    best.scored().freshness(),
                    best.scored().repeatExposurePenalty(),
                    best.diversityAdjustment(),
                    best.auxiliaryScore(),
                    ranked.size() + 1));
            authorCounts.merge(candidate.authorId(), 1, Integer::sum);
            for (String tag : candidate.tagSlugs()) {
                tagCounts.merge(normalize(tag), 1, Integer::sum);
            }
        }
        return List.copyOf(ranked);
    }

    private ScoredSearchCandidate score(
            SearchCandidate candidate,
            SearchInterestProfile profile,
            Instant referenceTime) {
        return new ScoredSearchCandidate(
                candidate,
                searchRelevance(candidate),
                interestMatch(candidate, profile),
                popularity(candidate),
                freshness(candidate, referenceTime),
                repeatExposurePenalty(candidate));
    }

    private int searchRelevance(SearchCandidate candidate) {
        int strongest = 0;
        int matchedFields = 0;
        if (candidate.titleExactMatch()) {
            strongest = Math.max(strongest, 1_000);
            matchedFields++;
        }
        if (candidate.titlePrefixMatch()) {
            strongest = Math.max(strongest, 850);
            matchedFields++;
        }
        if (candidate.titleContainsMatch()) {
            strongest = Math.max(strongest, 750);
            matchedFields++;
        }
        if (candidate.tagExactMatch()) {
            strongest = Math.max(strongest, 700);
            matchedFields++;
        }
        if (candidate.tagContainsMatch()) {
            strongest = Math.max(strongest, 600);
            matchedFields++;
        }
        if (candidate.regionExactMatch()) {
            strongest = Math.max(strongest, 500);
            matchedFields++;
        }
        if (candidate.regionContainsMatch()) {
            strongest = Math.max(strongest, 400);
            matchedFields++;
        }
        if (candidate.contentMatch()) {
            strongest = Math.max(strongest, 100);
            matchedFields++;
        }
        return strongest + Math.min(Math.max(0, matchedFields - 1), 9);
    }

    private double interestMatch(
            SearchCandidate candidate,
            SearchInterestProfile profile) {
        if (profile.featureStrengths().isEmpty()) {
            return 0.0d;
        }
        Set<String> candidateValues = new LinkedHashSet<>();
        candidateValues.add(normalize(candidate.regionSlug()));
        candidateValues.add(normalize(candidate.regionCode()));
        candidate.regionNames().stream().map(SearchRankingPolicy::normalize)
                .forEach(candidateValues::add);
        candidate.tagSlugs().stream().map(SearchRankingPolicy::normalize)
                .forEach(candidateValues::add);

        double total = 0.0d;
        int matched = 0;
        for (Map.Entry<String, Double> entry : profile.featureStrengths().entrySet()) {
            String featureValue = featureValue(entry.getKey());
            if (featureValue != null && candidateValues.stream()
                    .anyMatch(value -> value.equals(featureValue)
                            || value.endsWith("-" + featureValue)
                            || value.contains(featureValue))) {
                total += entry.getValue();
                matched++;
            }
        }
        if (matched == 0) {
            return 0.0d;
        }
        return clamp(total / matched, -1.0d, 1.0d);
    }

    private double popularity(SearchCandidate candidate) {
        double weighted = candidate.viewCount()
                + candidate.likeCount() * 4.0d
                + candidate.bookmarkCount() * 6.0d;
        return 1.0d - StrictMath.exp(-weighted / 100.0d);
    }

    private double freshness(SearchCandidate candidate, Instant referenceTime) {
        if (candidate.publishedAt().isAfter(referenceTime)) {
            return 0.0d;
        }
        double ageDays = Duration.between(candidate.publishedAt(), referenceTime)
                .toMillis() / 86_400_000.0d;
        return StrictMath.exp(-ageDays / 30.0d);
    }

    private double repeatExposurePenalty(SearchCandidate candidate) {
        return -Math.min(candidate.recentExposureCount(), 10) / 10.0d;
    }

    private CandidateWithAdjustment adjusted(
            ScoredSearchCandidate scored,
            Map<Long, Integer> authorCounts,
            Map<String, Integer> tagCounts) {
        SearchCandidate candidate = scored.candidate();
        int repeatedAuthor = authorCounts.getOrDefault(candidate.authorId(), 0);
        int repeatedTags = candidate.tagSlugs().stream()
                .map(SearchRankingPolicy::normalize)
                .mapToInt(tag -> tagCounts.getOrDefault(tag, 0))
                .sum();
        double diversity = -Math.min(1.0d, repeatedAuthor * 0.25d + repeatedTags * 0.05d);
        double auxiliary = scored.popularity() * 0.55d
                + scored.freshness() * 0.35d
                + scored.repeatExposurePenalty() * 0.05d
                + diversity * 0.05d;
        return new CandidateWithAdjustment(scored, diversity, auxiliary);
    }

    private Comparator<ScoredSearchCandidate> baseComparator() {
        return Comparator.comparingInt(ScoredSearchCandidate::searchRelevance).reversed()
                .thenComparing(Comparator.comparingDouble(
                        ScoredSearchCandidate::interestMatch).reversed())
                .thenComparing(Comparator.comparingDouble(
                        ScoredSearchCandidate::popularity).reversed())
                .thenComparing(Comparator.comparingDouble(
                        ScoredSearchCandidate::freshness).reversed())
                .thenComparing(Comparator.comparingDouble(
                        ScoredSearchCandidate::repeatExposurePenalty).reversed())
                .thenComparing(
                        scored -> scored.candidate().createdAt(),
                        Comparator.reverseOrder())
                .thenComparing(
                        scored -> scored.candidate().postId(),
                        Comparator.reverseOrder());
    }

    private Comparator<CandidateWithAdjustment> adjustedComparator() {
        return Comparator.comparingInt(
                        (CandidateWithAdjustment value) -> value.scored().searchRelevance())
                .reversed()
                .thenComparing(Comparator.comparingDouble(
                        (CandidateWithAdjustment value) -> value.scored().interestMatch()).reversed())
                .thenComparing(Comparator.comparingDouble(
                        CandidateWithAdjustment::auxiliaryScore).reversed())
                .thenComparing(
                        value -> value.scored().candidate().createdAt(),
                        Comparator.reverseOrder())
                .thenComparing(
                        value -> value.scored().candidate().postId(),
                        Comparator.reverseOrder());
    }

    private static String featureValue(String featureId) {
        if (featureId == null) {
            return null;
        }
        int separator = featureId.indexOf(':');
        if (separator < 0 || separator == featureId.length() - 1) {
            return null;
        }
        return normalize(featureId.substring(separator + 1));
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record SearchCandidate(
            long postId,
            long authorId,
            String regionCode,
            String regionSlug,
            List<String> regionNames,
            String title,
            List<String> tagSlugs,
            boolean titleExactMatch,
            boolean titlePrefixMatch,
            boolean titleContainsMatch,
            boolean tagExactMatch,
            boolean tagContainsMatch,
            boolean regionExactMatch,
            boolean regionContainsMatch,
            boolean contentMatch,
            Instant createdAt,
            Instant publishedAt,
            long viewCount,
            long likeCount,
            long bookmarkCount,
            int recentExposureCount) {

        public SearchCandidate {
            if (postId <= 0 || authorId <= 0) {
                throw new IllegalArgumentException("search candidate IDs must be positive");
            }
            Objects.requireNonNull(regionCode, "regionCode");
            Objects.requireNonNull(regionSlug, "regionSlug");
            regionNames = List.copyOf(Objects.requireNonNull(regionNames, "regionNames"));
            Objects.requireNonNull(title, "title");
            tagSlugs = List.copyOf(Objects.requireNonNull(tagSlugs, "tagSlugs"));
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(publishedAt, "publishedAt");
            if (viewCount < 0 || likeCount < 0 || bookmarkCount < 0
                    || recentExposureCount < 0) {
                throw new IllegalArgumentException("search candidate counters must be nonnegative");
            }
        }
    }

    public record RankedSearchCandidate(
            SearchCandidate candidate,
            int searchRelevance,
            double interestMatch,
            double popularity,
            double freshness,
            double repeatExposurePenalty,
            double diversityAdjustment,
            double auxiliaryScore,
            int rank) {}

    private record ScoredSearchCandidate(
            SearchCandidate candidate,
            int searchRelevance,
            double interestMatch,
            double popularity,
            double freshness,
            double repeatExposurePenalty) {}

    private record CandidateWithAdjustment(
            ScoredSearchCandidate scored,
            double diversityAdjustment,
            double auxiliaryScore) {}
}
