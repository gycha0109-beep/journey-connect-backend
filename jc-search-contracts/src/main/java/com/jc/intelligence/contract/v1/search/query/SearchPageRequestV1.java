package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.search.cursor.SearchCursorV1;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;

public record SearchPageRequestV1(int pageSize, SearchCursorV1 cursor) {
    public static final int MAX_PAGE_SIZE = 100;
    public SearchPageRequestV1 {
        SearchChecks.requireRange(pageSize, 1, MAX_PAGE_SIZE, "pageSize");
    }
    public static SearchPageRequestV1 firstPage(int pageSize) { return new SearchPageRequestV1(pageSize, null); }
}
