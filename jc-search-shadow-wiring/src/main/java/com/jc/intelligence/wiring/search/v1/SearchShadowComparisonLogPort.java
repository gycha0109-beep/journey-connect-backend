package com.jc.intelligence.wiring.search.v1;

public interface SearchShadowComparisonLogPort {
    boolean available();
    SearchShadowComparisonLogResultV1 log(SearchShadowStructuredRecordV1 record);
}
