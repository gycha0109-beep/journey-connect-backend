package com.jc.intelligence.runtime.search.v1.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SearchDeterministicOrdering {
    private SearchDeterministicOrdering() { }

    private static final Comparator<SearchRankedCandidateV1> COMPARATOR =
            Comparator.comparing(SearchRankedCandidateV1::rankingScore,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(SearchRankedCandidateV1::explicitOrderingKey,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(item -> item.candidate().sourceRank() == null
                            ? Integer.MAX_VALUE : item.candidate().sourceRank().intValue())
                    .thenComparing(item -> item.candidate().entityType().wireValue())
                    .thenComparing(item -> item.candidate().entityRef().value());

    public static List<SearchRankedCandidateV1> order(List<SearchRankedCandidateV1> source) {
        ArrayList<SearchRankedCandidateV1> copy = new ArrayList<>(source);
        copy.sort(COMPARATOR);
        return List.copyOf(copy);
    }
}
