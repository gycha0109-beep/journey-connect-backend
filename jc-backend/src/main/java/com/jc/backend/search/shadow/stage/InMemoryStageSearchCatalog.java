package com.jc.backend.search.shadow.stage;

import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalRequestV1;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalPort;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalResultV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankedCandidateV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingRequestV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingResultV1;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic synthetic catalog used only by test/stage shadow validation. */
public final class InMemoryStageSearchCatalog implements SearchRetrievalPort, SearchRankingPort {
    private static final SnapshotRef SNAPSHOT = new SnapshotRef("snapshot:ip10-stage-catalog-v1");
    private final List<StageSearchCatalogEntry> entries;
    private final AtomicLong retrievalInvocations = new AtomicLong();
    private final AtomicLong rankingInvocations = new AtomicLong();

    public InMemoryStageSearchCatalog() {
        this(List.of(
                new StageSearchCatalogEntry("stage-post-001", "서울 골목 여행", List.of("서울", "골목", "여행"),
                        List.of("서울", "kr-11"), 0.95d, "a-stage-post-001"),
                new StageSearchCatalogEntry("stage-post-002", "부산 바다 산책", List.of("부산", "바다", "산책"),
                        List.of("부산", "kr-26"), 0.90d, "b-stage-post-002"),
                new StageSearchCatalogEntry("stage-post-003", "제주 카페 기록", List.of("제주", "카페", "기록"),
                        List.of("제주", "kr-50"), 0.85d, "c-stage-post-003"),
                new StageSearchCatalogEntry("stage-post-004", "서울 야경 코스", List.of("서울", "야경", "코스"),
                        List.of("서울", "kr-11"), 0.80d, "d-stage-post-004")));
    }

    InMemoryStageSearchCatalog(List<StageSearchCatalogEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    @Override public SearchRetrievalResultV1 retrieve(RetrievalRequestV1 request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        retrievalInvocations.incrementAndGet();
        String normalizedQuery = request.query().normalizedQuery();
        List<String> queryTokens = normalizedQuery == null ? List.of() : List.of(normalizedQuery.split(" "));
        List<String> regionValues = request.filters().stream()
                .filter(filter -> filter.filterType() == SearchFilterType.REGION)
                .flatMap(filter -> filter.values().stream())
                .map(InMemoryStageSearchCatalog::normalize)
                .toList();
        ArrayList<RetrievalCandidateV1> candidates = new ArrayList<>();
        int rank = 1;
        for (StageSearchCatalogEntry entry : entries) {
            if (!matchesQuery(entry, queryTokens) || !matchesRegion(entry, regionValues)) continue;
            EntityRef ref = new EntityRef("post:" + entry.sourceId());
            candidates.add(new RetrievalCandidateV1(SearchContractIds.SEARCH_RETRIEVAL_RANKING, ref,
                    SearchEntityType.POST, entry.sourceId(), RetrievalSource.DATABASE_POST, entry.rankingScore(),
                    rank++, request.referenceTime(), SNAPSHOT, SearchEligibilityState.UNKNOWN,
                    SearchVisibilityState.UNKNOWN, null, request.retrievalStrategyVersion()));
            if (candidates.size() >= request.maximumCandidateCount()) break;
        }
        return SearchRetrievalResultV1.success(candidates);
    }

    @Override public SearchRankingResultV1 rank(SearchRankingRequestV1 request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        rankingInvocations.incrementAndGet();
        ArrayList<SearchRankedCandidateV1> ranked = new ArrayList<>();
        for (RetrievalCandidateV1 candidate : request.candidates()) {
            StageSearchCatalogEntry entry = entries.stream()
                    .filter(value -> value.sourceId().equals(candidate.sourceId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("candidate is not in stage catalog"));
            ranked.add(new SearchRankedCandidateV1(candidate, entry.rankingScore(), entry.orderingKey()));
        }
        return SearchRankingResultV1.success(ranked);
    }

    public long retrievalInvocationCount() { return retrievalInvocations.get(); }
    public long rankingInvocationCount() { return rankingInvocations.get(); }
    public int size() { return entries.size(); }

    private static boolean matchesQuery(StageSearchCatalogEntry entry, List<String> queryTokens) {
        if (queryTokens.isEmpty()) return true;
        String material = normalize(entry.title() + " " + String.join(" ", entry.queryTerms()));
        return queryTokens.stream().allMatch(material::contains);
    }

    private static boolean matchesRegion(StageSearchCatalogEntry entry, List<String> regions) {
        if (regions.isEmpty()) return true;
        List<String> entryRegions = entry.regionTerms().stream().map(InMemoryStageSearchCatalog::normalize).toList();
        return regions.stream().anyMatch(entryRegions::contains);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
