package com.jc.backend.recommendation.api;

import com.jc.backend.crew.CrewDtos;
import java.time.Instant;
import java.util.List;

public final class CrewRecommendationDtos {

    private CrewRecommendationDtos() {}

    public enum ReasonCode {
        TAG_INTEREST,
        REGION_INTEREST,
        TRAVEL_DATE_FIT,
        CAPACITY_REMAINING,
        FRESHNESS
    }

    public record Reason(ReasonCode code, double contribution) {}

    public record Item(
            int rank,
            CrewDtos.View crew,
            double score,
            String coverageMode,
            List<Reason> reasons) {
        public Item {
            reasons = List.copyOf(reasons);
        }
    }

    public record Response(
            String contractVersion,
            String rankingPolicyVersion,
            String scorePolicyVersion,
            Instant referenceTime,
            List<Item> items) {
        public Response {
            items = List.copyOf(items);
        }
    }
}
