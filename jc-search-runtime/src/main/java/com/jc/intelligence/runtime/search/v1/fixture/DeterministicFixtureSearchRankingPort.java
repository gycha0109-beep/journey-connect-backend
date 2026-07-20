package com.jc.intelligence.runtime.search.v1.fixture;

import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankedCandidateV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingRequestV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingResultV1;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class DeterministicFixtureSearchRankingPort implements SearchRankingPort {
    private final Map<EntityRef, Double> scores;
    private final Map<EntityRef, String> orderingKeys;

    public DeterministicFixtureSearchRankingPort(Map<EntityRef, Double> scores, Map<EntityRef, String> orderingKeys) {
        this.scores = Map.copyOf(Objects.requireNonNull(scores, "scores"));
        this.orderingKeys = Map.copyOf(Objects.requireNonNull(orderingKeys, "orderingKeys"));
    }

    @Override public SearchRankingResultV1 rank(SearchRankingRequestV1 request) {
        ArrayList<SearchRankedCandidateV1> ranked = new ArrayList<>();
        for (var candidate : request.candidates()) {
            ranked.add(new SearchRankedCandidateV1(candidate, scores.get(candidate.entityRef()),
                    orderingKeys.get(candidate.entityRef())));
        }
        return SearchRankingResultV1.success(ranked);
    }
}
