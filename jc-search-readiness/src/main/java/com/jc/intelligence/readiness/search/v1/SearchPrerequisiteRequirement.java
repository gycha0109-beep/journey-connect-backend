package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchPrerequisiteRequirement implements WireValue {
    REQUIRED_FOR_CONTROLLED_HOOK_PROPOSAL("required_for_controlled_hook_proposal"),
    REQUIRED_BEFORE_ACTIVATION("required_before_activation"),
    REQUIRED_BEFORE_CUTOVER("required_before_cutover"),
    NOT_REQUIRED_FOR_DISABLED_REGRESSION("not_required_for_disabled_regression");
    private final String wireValue;
    SearchPrerequisiteRequirement(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
