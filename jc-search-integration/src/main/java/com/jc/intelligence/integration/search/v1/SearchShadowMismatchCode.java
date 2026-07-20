package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowMismatchCode implements WireValue {
    LEGACY_SUCCESS_RUNTIME_SUCCESS("legacy_success_runtime_success"),
    LEGACY_SUCCESS_RUNTIME_NO_RESULTS("legacy_success_runtime_no_results"),
    LEGACY_SUCCESS_RUNTIME_FAILED("legacy_success_runtime_failed"),
    LEGACY_FAILURE_RUNTIME_SUCCESS("legacy_failure_runtime_success"),
    COUNT_MISMATCH("count_mismatch"),
    ENTITY_SET_MISMATCH("entity_set_mismatch"),
    ORDERING_MISMATCH("ordering_mismatch"),
    MISSING_ENTITY("missing_entity"),
    UNEXPECTED_ENTITY("unexpected_entity"),
    DUPLICATE_ENTITY("duplicate_entity"),
    POSITION_MISMATCH("position_mismatch"),
    RUNTIME_INPUT_UNAVAILABLE("runtime_input_unavailable"),
    RUNTIME_INPUT_UNSUPPORTED("runtime_input_unsupported"),
    RUNTIME_INPUT_INVALID("runtime_input_invalid"),
    RUNTIME_TIMEOUT("runtime_timeout"),
    RUNTIME_FAILURE("runtime_failure"),
    COMPARISON_FAILURE("comparison_failure"),
    UNSUPPORTED_LEGACY_SORT("unsupported_legacy_sort"),
    PAGINATION_NOT_COMPARABLE("pagination_not_comparable"),
    CURSOR_NOT_COMPARABLE("cursor_not_comparable"),
    VISIBILITY_NOT_COMPARABLE("visibility_not_comparable"),
    RANKING_NOT_COMPARABLE("ranking_not_comparable");

    private final String wireValue;
    SearchShadowMismatchCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
