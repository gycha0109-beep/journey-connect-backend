package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum LegacyExploreExplanationCode implements WireValue {
    LEGACY_QUERY_PREDICATE_APPLIED("legacy_query_predicate_applied"),
    LEGACY_REGION_FILTER_APPLIED("legacy_region_filter_applied"),
    LEGACY_PUBLISHED_AT_DESC_ID_DESC_ORDER("legacy_published_at_desc_id_desc_order");
    private final String wireValue;
    LegacyExploreExplanationCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
