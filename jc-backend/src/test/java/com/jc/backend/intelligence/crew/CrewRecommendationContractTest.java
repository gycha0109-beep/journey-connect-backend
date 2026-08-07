package com.jc.backend.intelligence.crew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jc.backend.intelligence.crew.CrewRecommendationContract.CandidateFacts;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.CoverageMode;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.EligibilityDecision;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.TagFeatureState;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.ViewerRelation;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.VisibilityState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrewRecommendationContractTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 7);

    @Test
    void contractStartsDisabledAndFullWeightsAreNormalized() {
        assertEquals("crew-recommendation-contract-v1", CrewRecommendationContract.CONTRACT_VERSION);
        assertEquals("crew-ranking-policy-v1", CrewRecommendationContract.POLICY_VERSION);
        assertEquals("crew_list", CrewRecommendationContract.SURFACE);
        assertEquals("crew-service-list-v1", CrewRecommendationContract.LEGACY_FALLBACK);
        assertTrue(!CrewRecommendationContract.DEFAULT_ENABLED);

        double sum = CrewRecommendationContract.TAG_INTEREST_WEIGHT
                + CrewRecommendationContract.REGION_INTEREST_WEIGHT
                + CrewRecommendationContract.TRAVEL_DATE_FIT_WEIGHT
                + CrewRecommendationContract.CAPACITY_REMAINING_WEIGHT
                + CrewRecommendationContract.FRESHNESS_WEIGHT;
        assertEquals(1.0d, sum, 1.0e-12);
        assertEquals(
                1.0d,
                CrewRecommendationContract.LEGACY_TAGLESS_REGION_WEIGHT
                        + CrewRecommendationContract.LEGACY_TAGLESS_FRESHNESS_WEIGHT,
                1.0e-12);
    }

    @Test
    void hardEligibilityFiltersRecruitingDateCapacityAndViewerRelationship() {
        CandidateFacts eligible = candidate(TagFeatureState.PRESENT, List.of("photo", "night"));
        assertEquals(
                EligibilityDecision.ELIGIBLE,
                CrewRecommendationContract.eligibility(
                        eligible, ViewerRelation.NONE, VisibilityState.NOT_INTEGRATED, REFERENCE_DATE));

        assertEquals(
                EligibilityDecision.NOT_RECRUITING,
                CrewRecommendationContract.eligibility(
                        withRecruiting(eligible, false),
                        ViewerRelation.NONE,
                        VisibilityState.NOT_INTEGRATED,
                        REFERENCE_DATE));
        assertEquals(
                EligibilityDecision.TRAVEL_DATE_ELAPSED,
                CrewRecommendationContract.eligibility(
                        withTravelDate(eligible, REFERENCE_DATE.minusDays(1)),
                        ViewerRelation.NONE,
                        VisibilityState.NOT_INTEGRATED,
                        REFERENCE_DATE));
        assertEquals(
                EligibilityDecision.CAPACITY_FULL,
                CrewRecommendationContract.eligibility(
                        withMembers(eligible, eligible.capacity()),
                        ViewerRelation.NONE,
                        VisibilityState.NOT_INTEGRATED,
                        REFERENCE_DATE));
        assertEquals(
                EligibilityDecision.VIEWER_IS_OWNER,
                CrewRecommendationContract.eligibility(
                        eligible, ViewerRelation.OWNER, VisibilityState.NOT_INTEGRATED, REFERENCE_DATE));
        assertEquals(
                EligibilityDecision.ALREADY_PENDING,
                CrewRecommendationContract.eligibility(
                        eligible, ViewerRelation.PENDING, VisibilityState.NOT_INTEGRATED, REFERENCE_DATE));
        assertEquals(
                EligibilityDecision.ALREADY_MEMBER,
                CrewRecommendationContract.eligibility(
                        eligible, ViewerRelation.APPROVED, VisibilityState.NOT_INTEGRATED, REFERENCE_DATE));
    }

    @Test
    void visibilityIsFailClosedOnlyAfterAnApprovedDecisionExists() {
        CandidateFacts eligible = candidate(TagFeatureState.EMPTY, List.of());

        assertEquals(
                EligibilityDecision.ELIGIBLE,
                CrewRecommendationContract.eligibility(
                        eligible, ViewerRelation.NONE, VisibilityState.NOT_INTEGRATED, REFERENCE_DATE));
        assertEquals(
                EligibilityDecision.ELIGIBLE,
                CrewRecommendationContract.eligibility(
                        eligible, ViewerRelation.NONE, VisibilityState.ELIGIBLE, REFERENCE_DATE));
        assertEquals(
                EligibilityDecision.VISIBILITY_INELIGIBLE,
                CrewRecommendationContract.eligibility(
                        eligible, ViewerRelation.NONE, VisibilityState.INELIGIBLE, REFERENCE_DATE));
    }

    @Test
    void historicalCancelledOrRejectedRelationshipDoesNotBlockReentry() {
        assertEquals(
                EligibilityDecision.ELIGIBLE,
                CrewRecommendationContract.eligibility(
                        candidate(TagFeatureState.UNAVAILABLE, List.of()),
                        ViewerRelation.HISTORY_ONLY,
                        VisibilityState.NOT_INTEGRATED,
                        REFERENCE_DATE));
    }

    @Test
    void tagCoverageNeverTreatsUnavailableTagsAsAZeroStrengthFullFeature() {
        assertEquals(
                CoverageMode.FULL_FEATURED,
                CrewRecommendationContract.coverageMode(
                        candidate(TagFeatureState.PRESENT, List.of("photo"))));
        assertEquals(
                CoverageMode.LEGACY_TAGLESS,
                CrewRecommendationContract.coverageMode(
                        candidate(TagFeatureState.EMPTY, List.of())));
        assertEquals(
                CoverageMode.LEGACY_TAGLESS,
                CrewRecommendationContract.coverageMode(
                        candidate(TagFeatureState.UNAVAILABLE, List.of())));
    }

    @Test
    void candidateFactsRejectAmbiguousTagCoverageAndInvalidIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate(TagFeatureState.PRESENT, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate(TagFeatureState.UNAVAILABLE, List.of("photo")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CandidateFacts(
                        0,
                        7,
                        "kr-11",
                        null,
                        10,
                        1,
                        true,
                        Instant.parse("2026-08-07T00:00:00Z"),
                        TagFeatureState.EMPTY,
                        List.of()));
    }

    @Test
    void entityReferenceUsesTheSystemContractCrewPrefix() {
        assertEquals("crew:42", CrewRecommendationContract.entityRef(42));
        assertThrows(IllegalArgumentException.class, () -> CrewRecommendationContract.entityRef(0));
    }

    private static CandidateFacts candidate(TagFeatureState state, List<String> tags) {
        return new CandidateFacts(
                11,
                7,
                "KR-11",
                REFERENCE_DATE.plusDays(14),
                10,
                3,
                true,
                Instant.parse("2026-08-01T00:00:00Z"),
                state,
                tags);
    }

    private static CandidateFacts withRecruiting(CandidateFacts source, boolean recruiting) {
        return new CandidateFacts(
                source.crewId(),
                source.ownerId(),
                source.regionCode(),
                source.travelDate(),
                source.capacity(),
                source.activeMemberCount(),
                recruiting,
                source.createdAt(),
                source.tagFeatureState(),
                source.tagSlugs());
    }

    private static CandidateFacts withTravelDate(CandidateFacts source, LocalDate travelDate) {
        return new CandidateFacts(
                source.crewId(),
                source.ownerId(),
                source.regionCode(),
                travelDate,
                source.capacity(),
                source.activeMemberCount(),
                source.recruiting(),
                source.createdAt(),
                source.tagFeatureState(),
                source.tagSlugs());
    }

    private static CandidateFacts withMembers(CandidateFacts source, long activeMemberCount) {
        return new CandidateFacts(
                source.crewId(),
                source.ownerId(),
                source.regionCode(),
                source.travelDate(),
                source.capacity(),
                activeMemberCount,
                source.recruiting(),
                source.createdAt(),
                source.tagFeatureState(),
                source.tagSlugs());
    }
}
