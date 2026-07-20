package com.jc.intelligence.runtime.search.v1.pagination;

import com.jc.intelligence.contract.v1.search.cursor.SearchCursorV1;
import java.util.Objects;

public record SearchTestCursorV1(
        SearchCursorV1 cursor,
        String authority,
        boolean productionAuthoritative) {
    public SearchTestCursorV1 {
        Objects.requireNonNull(cursor, "cursor");
        if (!"test_only_unsigned".equals(authority) || productionAuthoritative) {
            throw new IllegalArgumentException("IP-5 cursors must remain test_only_unsigned and non-authoritative");
        }
    }
}
