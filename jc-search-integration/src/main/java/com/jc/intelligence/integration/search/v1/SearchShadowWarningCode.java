package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowWarningCode implements WireValue {
    LEGACY_OFFSET_PRESERVED("legacy_offset_preserved"),
    LEGACY_LATEST_ORDER_NOT_RANKING("legacy_latest_order_not_ranking"),
    RAW_QUERY_OMITTED("raw_query_omitted"),
    NON_PERSISTENT_EVIDENCE("non_persistent_evidence"),
    NON_AUTHORITATIVE_METRIC("non_authoritative_metric"),
    PRODUCTION_WIRING_DISABLED("production_wiring_disabled");

    private final String wireValue;
    SearchShadowWarningCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
