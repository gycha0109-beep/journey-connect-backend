package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jc.backend.crew.CrewDtos;
import com.jc.backend.crew.CrewService;
import com.jc.backend.intelligence.crew.CrewRecommendationContract;
import com.jc.backend.intelligence.crew.CrewRecommendationRanker;
import com.jc.backend.intelligence.crew.CrewRecommendationService;
import com.jc.backend.recommendation.api.CrewRecommendationDtos;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CrewRecommendationApiServiceTest {

    @Test
    void commitsExposureAfterPresentationAndBeforeReturningPersonalizedResponse() {
        Fixture fixture = fixture();
        CrewRecommendationApiService service = new CrewRecommendationApiService(
                fixture.recommendationService(),
                fixture.crewService(),
                fixture.exposureService());

        CrewRecommendationDtos.Response response = service.find(77L, 5);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().crew().id()).isEqualTo(501L);

        InOrder order = inOrder(
                fixture.recommendationService(),
                fixture.crewService(),
                fixture.exposureService());
        order.verify(fixture.recommendationService()).recommend(eq(77L), eq(5), any(Instant.class));
        order.verify(fixture.crewService()).detail(77L, 501L);
        order.verify(fixture.exposureService()).commit(
                eq(77L),
                eq(5),
                eq(fixture.ranking()),
                any(CrewRecommendationDtos.Response.class),
                any(Instant.class));
    }

    @Test
    void exposurePersistenceFailureFailsClosed() {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("exposure persistence failed"))
                .when(fixture.exposureService())
                .commit(
                        eq(77L),
                        eq(5),
                        eq(fixture.ranking()),
                        any(CrewRecommendationDtos.Response.class),
                        any(Instant.class));
        CrewRecommendationApiService service = new CrewRecommendationApiService(
                fixture.recommendationService(),
                fixture.crewService(),
                fixture.exposureService());

        assertThatThrownBy(() -> service.find(77L, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("exposure persistence failed");
    }

    private static Fixture fixture() {
        CrewRecommendationService recommendationService = mock(CrewRecommendationService.class);
        CrewService crewService = mock(CrewService.class);
        CrewRecommendationExposureService exposureService =
                mock(CrewRecommendationExposureService.class);
        CrewRecommendationService.RecommendationResult ranking =
                mock(CrewRecommendationService.RecommendationResult.class);
        CrewRecommendationRanker.RankedCrew ranked = mock(CrewRecommendationRanker.RankedCrew.class);
        CrewRecommendationRanker.CandidateFacts facts =
                mock(CrewRecommendationRanker.CandidateFacts.class);
        CrewRecommendationRanker.ScoreBreakdown breakdown =
                mock(CrewRecommendationRanker.ScoreBreakdown.class);

        Instant referenceTime = Instant.parse("2026-08-25T06:00:00Z");
        when(recommendationService.recommend(eq(77L), eq(5), any(Instant.class))).thenReturn(ranking);
        when(ranking.contractVersion()).thenReturn("crew-recommendation-contract-v1");
        when(ranking.rankingPolicyVersion()).thenReturn("crew-ranking-policy-v1");
        when(ranking.scorePolicyVersion()).thenReturn("crew-score-policy-v1");
        when(ranking.referenceTime()).thenReturn(referenceTime);
        when(ranking.crews()).thenReturn(List.of(ranked));

        when(ranked.rank()).thenReturn(1);
        when(ranked.facts()).thenReturn(facts);
        when(ranked.breakdown()).thenReturn(breakdown);
        when(facts.crewId()).thenReturn(501L);
        when(breakdown.totalScore()).thenReturn(0.75d);
        when(breakdown.coverageMode())
                .thenReturn(CrewRecommendationContract.CoverageMode.LEGACY_TAGLESS);
        when(breakdown.regionInterest()).thenReturn(1.0d);
        when(breakdown.freshness()).thenReturn(0.5d);

        CrewDtos.View crew = new CrewDtos.View(
                501L,
                "Crew",
                "KR-11",
                "Seoul",
                "Description",
                null,
                10,
                3,
                0,
                true,
                false,
                1L,
                "owner",
                referenceTime,
                null);
        when(crewService.detail(77L, 501L)).thenReturn(crew);

        return new Fixture(recommendationService, crewService, exposureService, ranking);
    }

    private record Fixture(
            CrewRecommendationService recommendationService,
            CrewService crewService,
            CrewRecommendationExposureService exposureService,
            CrewRecommendationService.RecommendationResult ranking) {}
}
