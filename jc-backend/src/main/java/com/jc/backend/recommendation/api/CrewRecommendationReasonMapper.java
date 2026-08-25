package com.jc.backend.recommendation.api;

import com.jc.backend.intelligence.crew.CrewRecommendationContract;
import com.jc.backend.intelligence.crew.CrewRecommendationRanker.ScoreBreakdown;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Maps deterministic score components to stable user-facing reason codes. */
public final class CrewRecommendationReasonMapper {

    private CrewRecommendationReasonMapper() {}

    public static List<CrewRecommendationDtos.Reason> reasons(ScoreBreakdown breakdown) {
        List<CrewRecommendationDtos.Reason> reasons = new ArrayList<>();
        if (breakdown.coverageMode() == CrewRecommendationContract.CoverageMode.FULL_FEATURED) {
            add(reasons, CrewRecommendationDtos.ReasonCode.TAG_INTEREST,
                    CrewRecommendationContract.TAG_INTEREST_WEIGHT * breakdown.tagInterest());
            add(reasons, CrewRecommendationDtos.ReasonCode.REGION_INTEREST,
                    CrewRecommendationContract.REGION_INTEREST_WEIGHT * breakdown.regionInterest());
            add(reasons, CrewRecommendationDtos.ReasonCode.TRAVEL_DATE_FIT,
                    CrewRecommendationContract.TRAVEL_DATE_FIT_WEIGHT * breakdown.travelDateFit());
            add(reasons, CrewRecommendationDtos.ReasonCode.CAPACITY_REMAINING,
                    CrewRecommendationContract.CAPACITY_REMAINING_WEIGHT * breakdown.capacityRemaining());
            add(reasons, CrewRecommendationDtos.ReasonCode.FRESHNESS,
                    CrewRecommendationContract.FRESHNESS_WEIGHT * breakdown.freshness());
        } else {
            add(reasons, CrewRecommendationDtos.ReasonCode.REGION_INTEREST,
                    CrewRecommendationContract.LEGACY_TAGLESS_REGION_WEIGHT * breakdown.regionInterest());
            add(reasons, CrewRecommendationDtos.ReasonCode.FRESHNESS,
                    CrewRecommendationContract.LEGACY_TAGLESS_FRESHNESS_WEIGHT * breakdown.freshness());
        }
        return reasons.stream()
                .sorted(Comparator.comparingDouble(CrewRecommendationDtos.Reason::contribution)
                        .reversed()
                        .thenComparing(reason -> reason.code().ordinal()))
                .limit(3)
                .toList();
    }

    private static void add(
            List<CrewRecommendationDtos.Reason> reasons,
            CrewRecommendationDtos.ReasonCode code,
            double contribution) {
        if (contribution > 0.0d && Double.isFinite(contribution)) {
            reasons.add(new CrewRecommendationDtos.Reason(code, contribution));
        }
    }
}
