package com.jc.backend.search.shadow.stage;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchFilterSource;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.SearchSortType;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.query.SearchContextV1;
import com.jc.intelligence.contract.v1.search.query.SearchFilterV1;
import com.jc.intelligence.contract.v1.search.query.SearchPageRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryCanonicalizerV1;
import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchSortV1;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputResultV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Creates deterministic Search Runtime input from the legacy request only, never from the legacy response. */
public final class DefaultStageExploreSearchRuntimeInputProviderFactory
        implements StageExploreSearchRuntimeInputProviderFactory {
    private static final SchemaVersion RETRIEVAL_VERSION = new SchemaVersion("search-retrieval-stage-catalog-v1");
    private static final PolicyVersion RANKING_POLICY = new PolicyVersion("search-ranking-stage-fixture-v1");
    private static final ProducerBuildId BUILD = new ProducerBuildId("ip10-test-stage-shadow");
    private final int maximumCandidateCount;

    public DefaultStageExploreSearchRuntimeInputProviderFactory(int maximumCandidateCount) {
        if (maximumCandidateCount < 1 || maximumCandidateCount > 1000) {
            throw new IllegalArgumentException("maximumCandidateCount must be 1..1000");
        }
        this.maximumCandidateCount = maximumCandidateCount;
    }

    @Override public SearchShadowRuntimeInputProvider create(
            LegacyExploreRequestView legacyRequest,
            SearchShadowContextV1 shadowContext) {
        if (legacyRequest == null || shadowContext == null) throw new IllegalArgumentException("request/context are required");
        return ignored -> build(legacyRequest, shadowContext);
    }

    private SearchShadowRuntimeInputResultV1 build(
            LegacyExploreRequestView legacyRequest,
            SearchShadowContextV1 shadowContext) {
        if (legacyRequest.page() == null || legacyRequest.size() == null || legacyRequest.page() < 0
                || legacyRequest.size() < 1 || legacyRequest.size() > 100) {
            return SearchShadowRuntimeInputResultV1.invalid("legacy_page_invalid");
        }
        if (legacyRequest.page() != 0) return SearchShadowRuntimeInputResultV1.unsupported("legacy_offset_page_unsupported");
        if (!legacyRequest.sortOrders().isEmpty()) return SearchShadowRuntimeInputResultV1.unsupported("legacy_sort_unsupported");
        if (!legacyRequest.unsupportedParameters().isEmpty()) {
            return SearchShadowRuntimeInputResultV1.unsupported("legacy_filter_unsupported");
        }
        String rawQuery = blankToNull(legacyRequest.keyword());
        SearchQueryMode queryMode = rawQuery == null ? SearchQueryMode.BROWSE : SearchQueryMode.TEXT_QUERY;
        final var query = SearchQueryCanonicalizerV1.canonicalize(queryMode, rawQuery, "ko", "ko-KR");
        List<SearchFilterV1> filters = new ArrayList<>();
        String region = blankToNull(legacyRequest.region());
        if (region != null) {
            filters.add(new SearchFilterV1(SearchFilterType.REGION, List.of(region), SearchFilterSource.USER_SELECTED,
                    new SchemaVersion("search-filter-v1")));
        }
        SearchRequestV1 request = new SearchRequestV1(SearchContractIds.SEARCH_DOMAIN,
                safeId("request", shadowContext.correlationId()), shadowContext.correlationId(), query,
                new SearchContextV1(null, shadowContext.sessionRef(), SearchSurface.EXPLORE, SearchEntityScope.POST,
                        shadowContext.referenceTime(), "ko", "ko-KR", null, null), filters,
                new SearchSortV1(SearchSortType.RELEVANCE, RANKING_POLICY),
                SearchPageRequestV1.firstPage(legacyRequest.size()), new SchemaVersion("search-request-v1"),
                query.normalizationVersion(), RANKING_POLICY,
                new FeatureDefinitionVersion("search-feature-stage-fixture-v1"));
        Instant startedAt = shadowContext.referenceTime();
        SearchRuntimeExecutionRequestV1 execution = new SearchRuntimeExecutionRequestV1(request,
                new RunRef(safeId("run", shadowContext.correlationId())),
                new SchemaVersion("search-runtime-foundation-v1"), RETRIEVAL_VERSION,
                List.of(RetrievalSource.DATABASE_POST), new PolicyVersion("search-fallback-stage-fixture-v1"),
                BUILD, startedAt, startedAt.plusMillis(1), maximumCandidateCount, true);
        return SearchShadowRuntimeInputResultV1.available(execution, SearchShadowFingerprintV1.runtimeInput(execution));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value;
    }

    private static String safeId(String prefix, String material) {
        String hash = SearchShadowFingerprintV1.sha256(material.toLowerCase(Locale.ROOT));
        return prefix + ":ip10-" + hash.substring(0, 24);
    }
}
