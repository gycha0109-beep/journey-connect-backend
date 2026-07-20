package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.SearchSortType;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;

public final class SearchSortValidatorV1 {
    private SearchSortValidatorV1() { }
    public static void validate(SearchSortV1 sort, SearchQueryV1 query, SearchEntityScope scope) {
        SearchChecks.requireNonNull(sort, "sort");
        SearchChecks.requireNonNull(query, "query");
        SearchChecks.requireNonNull(scope, "scope");
        if (sort.sortType() == SearchSortType.RELEVANCE && query.queryMode() != SearchQueryMode.TEXT_QUERY) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_SORT_INVALID, "relevance sort requires text_query");
        }
        if (sort.sortType() == SearchSortType.DISTANCE
                && scope != SearchEntityScope.PLACE && scope != SearchEntityScope.ALL) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_SORT_INVALID, "distance sort requires place scope");
        }
    }
}
