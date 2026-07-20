package com.jc.intelligence.production.search.v1;

import java.util.List;
import java.util.Objects;

public record SearchProjectionQueryResultV1(SearchProjectionAvailabilityStatus status,
        List<SearchDocumentProjectionV1> documents, String safeReason) {
    public SearchProjectionQueryResultV1 {
        Objects.requireNonNull(status,"status"); documents=List.copyOf(Objects.requireNonNull(documents,"documents"));
        if (status==SearchProjectionAvailabilityStatus.AVAILABLE || status==SearchProjectionAvailabilityStatus.EMPTY) {
            if (safeReason!=null) throw new IllegalArgumentException("successful result cannot carry reason");
        } else if (safeReason==null || !safeReason.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("safe reason required");
    }
    public static SearchProjectionQueryResultV1 available(List<SearchDocumentProjectionV1> docs) {
        return new SearchProjectionQueryResultV1(docs.isEmpty()?SearchProjectionAvailabilityStatus.EMPTY:SearchProjectionAvailabilityStatus.AVAILABLE,docs,null);
    }
    public static SearchProjectionQueryResultV1 unavailable(SearchProjectionAvailabilityStatus status,String reason) {
        return new SearchProjectionQueryResultV1(status,List.of(),reason);
    }
}
