package com.jc.intelligence.wiring.search.v1;

public record SearchShadowActivationDecisionV1(boolean activated, String reasonCode) {
    public SearchShadowActivationDecisionV1 {
        if (reasonCode == null || !reasonCode.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("reasonCode must be lowercase_snake_case");
        }
        if (activated && !"activated_test_only".equals(reasonCode)) {
            throw new IllegalArgumentException("only explicit test-only activation is allowed");
        }
    }
}
