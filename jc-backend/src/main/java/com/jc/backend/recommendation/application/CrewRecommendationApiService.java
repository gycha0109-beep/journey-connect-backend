package com.jc.backend.recommendation.application;

import com.jc.backend.crew.CrewDtos;
import com.jc.backend.crew.CrewService;
import com.jc.backend.intelligence.crew.CrewRecommendationRanker;
import com.jc.backend.intelligence.crew.CrewRecommendationService;
import com.jc.backend.recommendation.api.CrewRecommendationDtos;
import com.jc.backend.recommendation.api.CrewRecommendationReasonMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Builds the authenticated Crew recommendation response without spanning APP and RECOMMENDATION
 * database roles in one transaction.
 */
@Service
public final class CrewRecommendationApiService {
    public static final int MAX_LIMIT = 20;

    private final CrewRecommendationService recommendationService;
    private final CrewService crewService;

    public CrewRecommendationApiService(
            CrewRecommendationService recommendationService,
            CrewService crewService) {
        this.recommendationService = recommendationService;
        this.crewService = crewService;
    }

    public CrewRecommendationDtos.Response find(long userId, int limit) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("crew recommendation limit must be 1.." + MAX_LIMIT);
        }

        Instant referenceTime = Instant.now();
        CrewRecommendationService.RecommendationResult ranking =
                recommendationService.recommend(userId, limit, referenceTime);
        List<CrewRecommendationDtos.Item> items = new ArrayList<>(ranking.crews().size());
        for (CrewRecommendationRanker.RankedCrew ranked : ranking.crews()) {
            CrewDtos.View crew = crewService.detail(userId, ranked.facts().crewId());
            items.add(new CrewRecommendationDtos.Item(
                    ranked.rank(),
                    crew,
                    ranked.breakdown().totalScore(),
                    ranked.breakdown().coverageMode().wireValue(),
                    CrewRecommendationReasonMapper.reasons(ranked.breakdown())));
        }
        return new CrewRecommendationDtos.Response(
                ranking.contractVersion(),
                ranking.rankingPolicyVersion(),
                ranking.scorePolicyVersion(),
                ranking.referenceTime(),
                items);
    }
}
