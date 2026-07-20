package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchProjectionWriteStatus implements WireValue {
    INSERTED("inserted"), UPDATED("updated"), UNCHANGED("unchanged"), REMOVED("removed"), STALE_IGNORED("stale_ignored"), HASH_MISMATCH_REJECTED("hash_mismatch_rejected"), SOURCE_MISSING("source_missing");
    private final String wireValue;
    SearchProjectionWriteStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
