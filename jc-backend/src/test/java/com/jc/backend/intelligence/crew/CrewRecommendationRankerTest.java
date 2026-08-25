package com.jc.backend.intelligence.crew;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.intelligence.crew.CrewRecommendationCandidateSource.Candidate;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.CandidateFacts;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.TagFeatureState;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.ViewerRelation;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import com.jc.recommendation.p1.profile.P1FeatureSignal;
import com.jc.recommendation.p1.profile.P1SignalSource;
import com.jc.recommendation.p1.profile.UserProfileSegment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrewRecommendationRankerTest {

    private static final Instant REFERENCE_TIME = Instant.parse("2026-08-25T04:00:00Z");
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 25);
    private final CrewRecommendationRanker ranker = new CrewRecommendationRanker();

    @Test
    void fullFeaturedRankingUsesProfileTagAndRegionSignalsDeterministically() {
        BehaviorProfileSnapshot profile = profile(List.of(
                signal("region:seoul", 0.8d),
                signal("theme:food", 1.0d)));
        Candidate preferred = candidate(11, "kr-seoul", List.of("food"), ViewerRelation.NONE);
        Candidate neutral = candidate(12, "kr-busan", List.of("nature"), ViewerRelation.NONE);

        List<CrewRecommendationRanker.RankedCrew> first = ranker.rank(
                List.of(neutral, preferred), profile, REFERENCE_TIME, REFERENCE_DATE, 10);
        List<CrewRecommendationRanker.RankedCrew> second = ranker.rank(
                List.of(preferred, neutral), profile, REFERENCE_TIME, REFERENCE_DATE, 10);

        assertThat(first).extracting(item -> item.facts().crewId()).containsExactly(11L, 12L);
        assertThat(second).extracting(item -> item.facts().crewId()).containsExactly(11L, 12L);
        assertThat(first.getFirst().breakdown().tagInterest()).isEqualTo(1.0d);
        assertThat(first.getFirst().breakdown().regionInterest()).isEqualTo(0.8d);
        assertThat(first.getFirst().breakdown().totalScore())
                .isGreaterThan(first.get(1).breakdown().totalScore());
    }

    @Test
    void hardEligibilityRemovesOwnerPendingMemberFullAndElapsedCrews() {
        BehaviorProfileSnapshot profile = profile(List.of());
        Candidate eligible = candidate(20, "kr-seoul", List.of(), ViewerRelation.NONE);
        Candidate owner = candidate(21, "kr-seoul", List.of(), ViewerRelation.OWNER);
        Candidate pending = candidate(22, "kr-seoul", List.of(), ViewerRelation.PENDING);
        Candidate member = candidate(23, "kr-seoul", List.of(), ViewerRelation.APPROVED);
        Candidate full = new Candidate(withMembers(candidateFacts(24, "kr-seoul", List.of()), 5), ViewerRelation.NONE);
        Candidate elapsed = new Candidate(withTravelDate(
                candidateFacts(25, "kr-seoul", List.of()), REFERENCE_DATE.minusDays(1)), ViewerRelation.NONE);

        assertThat(ranker.rank(
                        List.of(owner, pending, member, full, elapsed, eligible),
                        profile,
                        REFERENCE_TIME,
                        REFERENCE_DATE,
                        10))
                .extracting(item -> item.facts().crewId())
                .containsExactly(20L);
    }

    @Test
    void taglessCompatibilityUsesOnlyRegionAndFreshnessWeights() {
        BehaviorProfileSnapshot profile = profile(List.of(signal("region:seoul", 1.0d)));
        CandidateFacts facts = candidateFacts(30, "kr-seoul", List.of());

        CrewRecommendationRanker.RankedCrew ranked = ranker.rank(
                List.of(new Candidate(facts, ViewerRelation.NONE)),
                profile,
                REFERENCE_TIME,
                REFERENCE_DATE,
                1).getFirst();

        assertThat(ranked.breakdown().coverageMode())
                .isEqualTo(CrewRecommendationContract.CoverageMode.LEGACY_TAGLESS);
        assertThat(ranked.breakdown().travelDateFit()).isGreaterThan(0.0d);
        assertThat(ranked.breakdown().capacityRemaining()).isGreaterThan(0.0d);
        assertThat(ranked.breakdown().totalScore()).isEqualTo(
                0.75d * ranked.breakdown().regionInterest()
                        + 0.25d * ranked.breakdown().freshness());
    }

    private static P1FeatureSignal signal(String featureId, double strength) {
        return new P1FeatureSignal(
                featureId,
                PreferenceKind.PREFER,
                strength,
                strength,
                P1SignalSource.EXPLICIT);
    }

    private static BehaviorProfileSnapshot profile(List<P1FeatureSignal> signals) {
        return new BehaviorProfileSnapshot(
                "100",
                REFERENCE_TIME,
                "behavior-profile-policy-v1",
                "feature-vocabulary-v2",
                signals.isEmpty() ? UserProfileSegment.EMPTY : UserProfileSegment.EXPLICIT_ONLY,
                signals.size(),
                0,
                0,
                0,
                0,
                0.0d,
                signals,
                "a".repeat(64));
    }

    private static Candidate candidate(long crewId, String regionSlug, List<String> tags, ViewerRelation relation) {
        return new Candidate(candidateFacts(crewId, regionSlug, tags), relation);
    }

    private static CandidateFacts candidateFacts(long crewId, String regionSlug, List<String> tags) {
        return new CandidateFacts(
                crewId,
                900 + crewId,
                regionSlug.toUpperCase(),
                regionSlug,
                REFERENCE_DATE.plusDays(10),
                5,
                1,
                true,
                REFERENCE_TIME.minusSeconds(86_400),
                tags.isEmpty() ? TagFeatureState.EMPTY : TagFeatureState.PRESENT,
                tags);
    }

    private static CandidateFacts withMembers(CandidateFacts source, long activeMembers) {
        return new CandidateFacts(
                source.crewId(), source.ownerId(), source.regionCode(), source.regionSlug(), source.travelDate(),
                source.capacity(), activeMembers, source.recruiting(), source.createdAt(),
                source.tagFeatureState(), source.tagSlugs());
    }

    private static CandidateFacts withTravelDate(CandidateFacts source, LocalDate travelDate) {
        return new CandidateFacts(
                source.crewId(), source.ownerId(), source.regionCode(), source.regionSlug(), travelDate,
                source.capacity(), source.activeMemberCount(), source.recruiting(), source.createdAt(),
                source.tagFeatureState(), source.tagSlugs());
    }
}
