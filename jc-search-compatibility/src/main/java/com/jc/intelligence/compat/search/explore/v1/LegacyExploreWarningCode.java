package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum LegacyExploreWarningCode implements WireValue {
    LEGACY_OFFSET_PAGINATION("legacy_offset_pagination"),
    SEARCH_CURSOR_UNAVAILABLE("search_cursor_unavailable"),
    SEARCH_RUN_AUTHORITY_UNAVAILABLE("search_run_authority_unavailable"),
    SEARCH_EXPOSURE_AUTHORITY_UNAVAILABLE("search_exposure_authority_unavailable"),
    RETRIEVAL_SCORE_UNAVAILABLE("retrieval_score_unavailable"),
    SOURCE_SNAPSHOT_UNAVAILABLE("source_snapshot_unavailable"),
    RANKING_POLICY_AUTHORITY_UNAVAILABLE("ranking_policy_authority_unavailable"),
    MATCH_FIELD_UNAVAILABLE("match_field_unavailable"),
    VISIBILITY_EVIDENCE_NOT_MATERIALIZED("visibility_evidence_not_materialized");
    private final String wireValue;
    LegacyExploreWarningCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static LegacyExploreWarningCode fromWire(String value) {
        for (LegacyExploreWarningCode item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown warning code: " + value);
    }
}
