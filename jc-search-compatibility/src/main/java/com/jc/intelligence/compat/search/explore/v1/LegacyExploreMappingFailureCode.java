package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum LegacyExploreMappingFailureCode implements WireValue {
    INVALID_LEGACY_REQUEST("invalid_legacy_request"),
    UNSUPPORTED_LEGACY_FILTER("unsupported_legacy_filter"),
    UNSUPPORTED_LEGACY_SORT("unsupported_legacy_sort"),
    MISSING_REQUIRED_LEGACY_FIELD("missing_required_legacy_field"),
    UNMAPPABLE_ENTITY_TYPE("unmappable_entity_type"),
    INVALID_PAGE_METADATA("invalid_page_metadata"),
    INVALID_TIMESTAMP("invalid_timestamp"),
    CONTRACT_VALIDATION_FAILURE("contract_validation_failure"),
    LEGACY_PAYLOAD_INCONSISTENCY("legacy_payload_inconsistency"),
    DUPLICATE_ITEM_REFERENCE("duplicate_item_reference");
    private final String wireValue;
    LegacyExploreMappingFailureCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static LegacyExploreMappingFailureCode fromWire(String value) {
        for (LegacyExploreMappingFailureCode item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown mapping failure code: " + value);
    }
}
