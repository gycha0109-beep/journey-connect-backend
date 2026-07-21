package com.jc.backend.search.shadow.production;

public enum ProductionSearchShadowActivationReason {
    DISABLED_BY_CONFIGURATION("disabled_by_configuration"),
    DISABLED_BY_KILL_SWITCH("disabled_by_kill_switch"),
    ZERO_SAMPLING("zero_sampling"),
    OPERATIONAL_INPUTS_MISSING("operational_inputs_missing"),
    ACTIVATION_WINDOW_CLOSED("activation_window_closed"),
    EMPTY_ALLOWLIST("empty_allowlist"),
    ANONYMOUS_SUBJECT("anonymous_subject"),
    NOT_IN_COHORT("not_in_cohort"),
    NOT_SAMPLED("not_sampled"),
    RESOURCE_REJECTED("resource_rejected"),
    DISPATCHED("dispatched");

    private final String safeCode;
    ProductionSearchShadowActivationReason(String safeCode) { this.safeCode = safeCode; }
    public String safeCode() { return safeCode; }
}
