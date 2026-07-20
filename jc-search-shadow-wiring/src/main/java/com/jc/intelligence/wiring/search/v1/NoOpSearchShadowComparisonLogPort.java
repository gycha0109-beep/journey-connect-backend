package com.jc.intelligence.wiring.search.v1;

public final class NoOpSearchShadowComparisonLogPort implements SearchShadowComparisonLogPort {
    @Override public boolean available() { return true; }
    @Override public SearchShadowComparisonLogResultV1 log(SearchShadowStructuredRecordV1 record) {
        if (record == null) throw new IllegalArgumentException("record is required");
        return new SearchShadowComparisonLogResultV1(SearchShadowLogStatus.SKIPPED, "no_op");
    }
}
