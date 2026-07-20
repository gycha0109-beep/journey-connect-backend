package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowReadinessDecision implements WireValue {
    READY_FOR_CONTROLLED_HOOK_PROPOSAL("ready_for_controlled_hook_proposal"),
    HOLD_FOR_ARCHITECTURE_CHANGE("hold_for_architecture_change"),
    HOLD_FOR_OWNER_DECISIONS("hold_for_owner_decisions"),
    NOT_READY("not_ready");
    private final String wireValue;
    SearchShadowReadinessDecision(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
