package com.jc.backend.intelligence.crew;

import com.jc.backend.intelligence.crew.CrewRecommendationCandidateSource.Candidate;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.CandidateFacts;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.CoverageMode;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.EligibilityDecision;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.VisibilityState;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import com.jc.recommendation.p1.profile.P1FeatureSignal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CrewRecommendationRanker {

    public static final String SCORE_POLICY_VERSION = "crew-score-policy-v1";
    public static final int TRAVEL_DATE_HORIZON_DAYS = 90;
    public static final double FRESHNESS_HALF_LIFE_DAYS = 30.0d;

    public List<RankedCrew> rank(
            List<Candidate> candidates,
            BehaviorProfileSnapshot profile,
            Instant referenceTime,
            LocalDate referenceDate,
            int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Map<String, Double> signals = signals(profile);

        List<ScoredCrew> scored = candidates.stream()
                .filter(candidate -> CrewRecommendationContract.eligibility(
                                candidate.facts(),
                                candidate.viewerRelation(),
                                VisibilityState.NOT_INTEGRATED,
                                referenceDate)
                        == EligibilityDecision.ELIGIBLE)
                .map(candidate -> score(candidate.facts(), signals, referenceTime, referenceDate))
                .sorted(Comparator.comparingDouble(ScoredCrew::score)
                        .reversed()
                        .thenComparing((ScoredCrew item) -> item.facts().createdAt(), Comparator.reverseOrder())
                        .thenComparing((ScoredCrew item) -> item.facts().crewId(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        java.util.ArrayList<RankedCrew> result = new java.util.ArrayList<>(scored.size());
        for (int index = 0; index < scored.size(); index++) {
            ScoredCrew item = scored.get(index);
            result.add(new RankedCrew(index + 1, item.facts(), item.breakdown()));
        }
        return List.copyOf(result);
    }

    private ScoredCrew score(
            CandidateFacts facts,
            Map<String, Double> signals,
            Instant referenceTime,
            LocalDate referenceDate) {
        double tagInterest = tagInterest(facts.tagSlugs(), signals);
        double regionInterest = signal(signals, CrewRecommendationFeatureMapper.regionFeature(facts.regionSlug()));
        double travelDateFit = travelDateFit(facts.travelDate(), referenceDate);
        double capacityRemaining = capacityRemaining(facts);
        double freshness = freshness(facts.createdAt(), referenceTime);
        CoverageMode mode = CrewRecommendationContract.coverageMode(facts);

        double score;
        if (mode == CoverageMode.FULL_FEATURED) {
            score = CrewRecommendationContract.TAG_INTEREST_WEIGHT * tagInterest
                    + CrewRecommendationContract.REGION_INTEREST_WEIGHT * regionInterest
                    + CrewRecommendationContract.TRAVEL_DATE_FIT_WEIGHT * travelDateFit
                    + CrewRecommendationContract.CAPACITY_REMAINING_WEIGHT * capacityRemaining
                    + CrewRecommendationContract.FRESHNESS_WEIGHT * freshness;
        } else {
            score = CrewRecommendationContract.LEGACY_TAGLESS_REGION_WEIGHT * regionInterest
                    + CrewRecommendationContract.LEGACY_TAGLESS_FRESHNESS_WEIGHT * freshness;
        }

        ScoreBreakdown breakdown = new ScoreBreakdown(
                SCORE_POLICY_VERSION,
                mode,
                tagInterest,
                regionInterest,
                travelDateFit,
                capacityRemaining,
                freshness,
                score);
        return new ScoredCrew(facts, breakdown);
    }

    private static Map<String, Double> signals(BehaviorProfileSnapshot profile) {
        Map<String, Double> result = new HashMap<>();
        for (P1FeatureSignal signal : profile.signals()) {
            double signedStrength = signal.direction() == PreferenceKind.PREFER
                    ? signal.strength()
                    : -signal.strength();
            result.put(signal.featureId(), signedStrength);
        }
        return Map.copyOf(result);
    }

    private static double tagInterest(List<String> tagSlugs, Map<String, Double> signals) {
        double total = 0.0d;
        int mapped = 0;
        for (String tagSlug : tagSlugs) {
            String feature = CrewRecommendationFeatureMapper.tagFeature(tagSlug);
            if (feature == null) {
                continue;
            }
            mapped++;
            total += signal(signals, feature);
        }
        return mapped == 0 ? 0.0d : total / mapped;
    }

    private static double signal(Map<String, Double> signals, String feature) {
        return feature == null ? 0.0d : signals.getOrDefault(feature, 0.0d);
    }

    private static double travelDateFit(LocalDate travelDate, LocalDate referenceDate) {
        if (travelDate == null) {
            return 0.0d;
        }
        long days = ChronoUnit.DAYS.between(referenceDate, travelDate);
        if (days <= 0) {
            return 1.0d;
        }
        if (days >= TRAVEL_DATE_HORIZON_DAYS) {
            return 0.0d;
        }
        return 1.0d - ((double) days / TRAVEL_DATE_HORIZON_DAYS);
    }

    private static double capacityRemaining(CandidateFacts facts) {
        return facts.capacity() <= 0
                ? 0.0d
                : Math.min(1.0d, (double) facts.capacityRemaining() / facts.capacity());
    }

    private static double freshness(Instant createdAt, Instant referenceTime) {
        if (!createdAt.isBefore(referenceTime)) {
            return 1.0d;
        }
        double ageDays = Duration.between(createdAt, referenceTime).toMillis() / 86_400_000.0d;
        return StrictMath.pow(0.5d, ageDays / FRESHNESS_HALF_LIFE_DAYS);
    }

    private record ScoredCrew(CandidateFacts facts, ScoreBreakdown breakdown) {
        double score() {
            return breakdown.totalScore();
        }
    }

    public record RankedCrew(int rank, CandidateFacts facts, ScoreBreakdown breakdown) {}

    public record ScoreBreakdown(
            String scorePolicyVersion,
            CoverageMode coverageMode,
            double tagInterest,
            double regionInterest,
            double travelDateFit,
            double capacityRemaining,
            double freshness,
            double totalScore) {}
}
