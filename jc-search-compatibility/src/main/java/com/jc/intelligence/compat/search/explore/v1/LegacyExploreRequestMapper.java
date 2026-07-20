package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchFilterSource;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.SearchSortType;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.query.SearchContextV1;
import com.jc.intelligence.contract.v1.search.query.SearchFilterV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryCanonicalizerV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryV1;
import com.jc.intelligence.contract.v1.search.query.SearchSortV1;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.util.ArrayList;
import java.util.List;

public final class LegacyExploreRequestMapper {
    public LegacyExploreMappedRequest map(
            LegacyExploreRequestView legacy, LegacyExploreCompatibilityContext context) {
        if (legacy == null || context == null || context.referenceTime() == null || context.mappedAt() == null
                || context.producerBuildId() == null) {
            throw invalid(LegacyExploreMappingFailureCode.INVALID_LEGACY_REQUEST, null, "request/context is incomplete");
        }
        if (context.mappedAt().isBefore(context.referenceTime())) {
            throw invalid(LegacyExploreMappingFailureCode.INVALID_TIMESTAMP, "mappedAt", "mappedAt precedes referenceTime");
        }
        try {
            if (context.requestId() != null) com.jc.intelligence.contract.v1.search.validation.SearchChecks.requireOpaqueId(context.requestId(), "requestId");
            if (context.correlationId() != null) com.jc.intelligence.contract.v1.search.validation.SearchChecks.requireOpaqueId(context.correlationId(), "correlationId");
        } catch (RuntimeException exception) {
            throw invalid(LegacyExploreMappingFailureCode.CONTRACT_VALIDATION_FAILURE, "requestContext", exception.getMessage());
        }
        if (legacy.page() == null || legacy.page() < 0 || legacy.size() == null || legacy.size() < 1
                || legacy.size() > LegacyExploreCompatibilityPolicy.MAX_PAGE_SIZE) {
            throw invalid(LegacyExploreMappingFailureCode.INVALID_LEGACY_REQUEST, "page", "invalid page or size");
        }
        if (!legacy.unsupportedParameters().isEmpty()) {
            throw unsupported(LegacyExploreMappingFailureCode.UNSUPPORTED_LEGACY_FILTER,
                    legacy.unsupportedParameters().keySet().iterator().next(), "unsupported legacy parameter");
        }
        if (!legacy.sortOrders().isEmpty()) {
            throw unsupported(LegacyExploreMappingFailureCode.UNSUPPORTED_LEGACY_SORT, "sort",
                    "client-provided Pageable sort is not asserted as the legacy repository default order");
        }
        try {
            String keyword = blankToNull(legacy.keyword());
            SearchQueryV1 query = SearchQueryCanonicalizerV1.canonicalize(
                    keyword == null ? SearchQueryMode.BROWSE : SearchQueryMode.TEXT_QUERY, keyword, null, null);
            List<SearchFilterV1> filters = new ArrayList<>();
            String region = blankToNull(legacy.region());
            if (region != null) {
                filters.add(new SearchFilterV1(SearchFilterType.REGION, List.of(region),
                        SearchFilterSource.USER_SELECTED, new SchemaVersion("search-filter-v1")));
            }
            SearchContextV1 searchContext = new SearchContextV1(
                    null, context.sessionRef(), SearchSurface.EXPLORE, SearchEntityScope.POST,
                    context.referenceTime(), null, null, null, null);
            SearchSortV1 sort = new SearchSortV1(SearchSortType.RECENT, LegacyExploreContractIds.ORDER_POLICY);
            return new LegacyExploreMappedRequest(query, searchContext, filters, sort, legacy.page(), legacy.size(), false, false);
        } catch (RuntimeException exception) {
            if (exception instanceof LegacyExploreMappingException mapping) throw mapping;
            throw invalid(LegacyExploreMappingFailureCode.CONTRACT_VALIDATION_FAILURE, null, exception.getMessage());
        }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static LegacyExploreMappingException invalid(
            LegacyExploreMappingFailureCode code, String field, String message) {
        return new LegacyExploreMappingException(LegacyExploreCompatibilityStatus.INVALID_INPUT,
                new LegacyExploreMappingFailure(code, field, null, message));
    }
    private static LegacyExploreMappingException unsupported(
            LegacyExploreMappingFailureCode code, String field, String message) {
        return new LegacyExploreMappingException(LegacyExploreCompatibilityStatus.UNSUPPORTED,
                new LegacyExploreMappingFailure(code, field, null, message));
    }
}
