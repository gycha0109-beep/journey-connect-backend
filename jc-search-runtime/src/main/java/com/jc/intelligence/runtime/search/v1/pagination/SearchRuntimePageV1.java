package com.jc.intelligence.runtime.search.v1.pagination;

import com.jc.intelligence.runtime.search.v1.snapshot.SearchResultItemV1;
import java.util.List;
import java.util.Objects;

public record SearchRuntimePageV1(
        List<SearchResultItemV1> items,
        int pageSize,
        boolean hasNext,
        SearchTestCursorV1 nextCursor) {
    public SearchRuntimePageV1 {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be 1..100");
        if (items.size() > pageSize) throw new IllegalArgumentException("page items cannot exceed pageSize");
        if (hasNext != (nextCursor != null)) throw new IllegalArgumentException("hasNext and nextCursor must agree");
    }
}
