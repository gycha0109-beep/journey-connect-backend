package com.jc.intelligence.production.search.v1;

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

/** Builds transient runtime input from the legacy request only; candidates come from the projection retrieval port. */
public final class ProjectionExploreRuntimeInputProviderFactory {
    private static final PolicyVersion RANKING_POLICY = new PolicyVersion("search-ranking-projection-shadow-v1");
    private static final ProducerBuildId BUILD = new ProducerBuildId("ip11-5-production-shadow-technical-controls");
    private final int maximumCandidateCount;
    public ProjectionExploreRuntimeInputProviderFactory(int maximumCandidateCount) {
        if (maximumCandidateCount < 1 || maximumCandidateCount > 1000) throw new IllegalArgumentException("maximumCandidateCount must be 1..1000");
        this.maximumCandidateCount = maximumCandidateCount;
    }
    public SearchShadowRuntimeInputProvider create(LegacyExploreRequestView legacyRequest, SearchShadowContextV1 context) {
        if (legacyRequest == null || context == null) throw new IllegalArgumentException("request/context required");
        return ignored -> build(legacyRequest, context);
    }
    private SearchShadowRuntimeInputResultV1 build(LegacyExploreRequestView legacyRequest, SearchShadowContextV1 context) {
        if (legacyRequest.page() == null || legacyRequest.size() == null || legacyRequest.page() < 0 || legacyRequest.size() < 1 || legacyRequest.size() > 100) return SearchShadowRuntimeInputResultV1.invalid("legacy_page_invalid");
        if (legacyRequest.page() != 0) return SearchShadowRuntimeInputResultV1.unsupported("legacy_offset_page_unsupported");
        if (!legacyRequest.sortOrders().isEmpty()) return SearchShadowRuntimeInputResultV1.unsupported("legacy_sort_unsupported");
        if (!legacyRequest.unsupportedParameters().isEmpty()) return SearchShadowRuntimeInputResultV1.unsupported("legacy_filter_unsupported");
        String rawQuery = blankToNull(legacyRequest.keyword());
        SearchQueryMode mode = rawQuery == null ? SearchQueryMode.BROWSE : SearchQueryMode.TEXT_QUERY;
        var query = SearchQueryCanonicalizerV1.canonicalize(mode, rawQuery, "ko", "ko-KR");
        List<SearchFilterV1> filters = new ArrayList<>();
        String region = canonicalRegion(legacyRequest.region());
        if (region != null) filters.add(new SearchFilterV1(SearchFilterType.REGION, List.of(region), SearchFilterSource.USER_SELECTED, new SchemaVersion("search-filter-v1")));
        SearchRequestV1 request = new SearchRequestV1(SearchContractIds.SEARCH_DOMAIN,
                safeId("request", context.correlationId()), context.correlationId(), query,
                new SearchContextV1(null, context.sessionRef(), SearchSurface.EXPLORE, SearchEntityScope.POST,
                        context.referenceTime(), "ko", "ko-KR", null, null), filters,
                new SearchSortV1(SearchSortType.RELEVANCE, RANKING_POLICY), SearchPageRequestV1.firstPage(legacyRequest.size()),
                new SchemaVersion("search-request-v1"), query.normalizationVersion(), RANKING_POLICY,
                new FeatureDefinitionVersion("search-feature-projection-shadow-v1"));
        Instant started = context.referenceTime();
        SearchRuntimeExecutionRequestV1 execution = new SearchRuntimeExecutionRequestV1(request,
                new RunRef(safeId("run", context.correlationId())), new SchemaVersion("search-runtime-foundation-v1"),
                SearchProductionContractIds.RETRIEVAL_STRATEGY, List.of(RetrievalSource.DATABASE_POST),
                new PolicyVersion("search-fallback-projection-shadow-v1"), BUILD, started, started.plusMillis(1),
                maximumCandidateCount, true);
        return SearchShadowRuntimeInputResultV1.available(execution, SearchShadowFingerprintV1.runtimeInput(execution));
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String canonicalRegion(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (normalized.startsWith("-")) normalized = normalized.substring(1);
        if (normalized.endsWith("-")) normalized = normalized.substring(0, normalized.length()-1);
        return normalized.isBlank() || normalized.length() > 80 ? null : normalized;
    }
    private static String safeId(String prefix, String material) {
        String hash = SearchShadowFingerprintV1.sha256(material.toLowerCase(Locale.ROOT));
        return prefix + ":ip115-" + hash.substring(0, 24);
    }
}
