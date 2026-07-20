package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.runtime.search.v1.DefaultSearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.fixture.PassThroughSearchCandidateFilter;
import com.jc.intelligence.runtime.search.v1.port.SearchDependencyDecision;
import com.jc.intelligence.runtime.search.v1.ranking.NoOpSearchRerankingPort;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankedCandidateV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingResultV1;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

public final class ProductionProjectionSearchRuntimeFactory {
    private ProductionProjectionSearchRuntimeFactory() { }
    public static SearchRuntime create(SearchProjectionStore store, Duration maximumStaleness) {
        Objects.requireNonNull(store, "store");
        var retrieval = new ProjectionSearchRetrievalPort(store, maximumStaleness);
        return new DefaultSearchRuntime(retrieval, new PassThroughSearchCandidateFilter(),
                (request, candidate) -> SearchDependencyDecision.ALLOW,
                (request, candidate) -> SearchDependencyDecision.ALLOW,
                rankingRequest -> {
                    var ranked = new ArrayList<SearchRankedCandidateV1>();
                    for (var candidate : rankingRequest.candidates()) {
                        double score = candidate.sourceRank() == null ? 0.0 : 1.0 / candidate.sourceRank();
                        ranked.add(new SearchRankedCandidateV1(candidate, score,
                                String.format("%08d", candidate.sourceRank() == null ? Integer.MAX_VALUE : candidate.sourceRank())));
                    }
                    ranked.sort(Comparator.comparing(SearchRankedCandidateV1::explicitOrderingKey));
                    return SearchRankingResultV1.success(ranked);
                },
                new NoOpSearchRerankingPort());
    }
}
