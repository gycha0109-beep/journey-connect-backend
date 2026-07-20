package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.v1.search.query.SearchContextV1;
import com.jc.intelligence.contract.v1.search.query.SearchFilterV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryV1;
import com.jc.intelligence.contract.v1.search.query.SearchSortV1;
import java.util.ArrayList;
import java.util.List;

public record LegacyExploreMappedRequest(
        SearchQueryV1 query, SearchContextV1 context, List<SearchFilterV1> filters, SearchSortV1 sort,
        int legacyPage, int legacySize, boolean cursorAvailable, boolean searchRequestAuthority) {
    public LegacyExploreMappedRequest {
        java.util.Objects.requireNonNull(query, "query");
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(sort, "sort");
        filters = List.copyOf(new ArrayList<>(java.util.Objects.requireNonNull(filters, "filters")));
        if (legacyPage < 0 || legacySize < 1 || legacySize > LegacyExploreCompatibilityPolicy.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("legacy page request is out of range");
        }
        if (cursorAvailable || searchRequestAuthority) {
            throw new IllegalArgumentException("legacy request mapping must remain non-authoritative and cursor-free");
        }
    }
}
