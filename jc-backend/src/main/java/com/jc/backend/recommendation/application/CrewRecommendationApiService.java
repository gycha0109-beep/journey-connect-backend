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

@Service
public final class CrewRecommendationApiService {
    public static final int MAX_LIMIT = 20;

    private final CrewRecommendationService recommendationService;
    private final CrewService crewService;
    private final CrewRecommendationExposureService exposureService;

    public CrewRecommendationApiService(
            CrewRecommendationService recommendationService,
            CrewService crewService,
            CrewRecommendationExposureService exposureService) {
        this.recommendationService = recommendationService;
        this.crewService = crewService;
        this.exposureService = exposureService;
    }

    public CrewRecommendationDtos.Response find(long userId, int limit) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Crew recommendation user ID must be positive.");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Crew recommendation limit must be in 1.." + MAX_LIMIT + ".");
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

        CrewRecommendationDtos.Response response = new CrewRecommendationDtos.Response(
                ranking.contractVersion(),
                ranking.rankingPolicyVersion(),
                ranking.scorePolicyVersion(),
                ranking.referenceTime(),
                items);

        exposureService.commit(userId, limit, ranking, response, Instant.now());
        return response;
    }
}
