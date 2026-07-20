package com.jc.intelligence.integration.search.v1;

public record SearchShadowActivationDecisionV1(
        SearchShadowMode mode,
        boolean activated,
        boolean testOnly,
        String safeReason) {
    public SearchShadowActivationDecisionV1 {
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (safeReason == null || safeReason.isBlank() || !safeReason.equals(safeReason.trim())) {
            throw new IllegalArgumentException("safeReason is required");
        }
        switch (mode) {
            case DISABLED -> {
                if (activated || testOnly) throw new IllegalArgumentException("disabled mode cannot activate shadow");
            }
            case TEST_ONLY -> {
                if (!activated || !testOnly) throw new IllegalArgumentException("test_only must be activated and test-only");
            }
            case SHADOW_ENABLED -> {
                if (!activated || testOnly) throw new IllegalArgumentException("shadow_enabled must be activated and not test-only");
            }
        }
    }

    public static SearchShadowActivationDecisionV1 decide(SearchShadowPolicyV1 policy) {
        if (policy == null) throw new IllegalArgumentException("policy is required");
        return switch (policy.mode()) {
            case DISABLED -> new SearchShadowActivationDecisionV1(SearchShadowMode.DISABLED, false, false,
                    "shadow integration disabled by policy");
            case TEST_ONLY -> new SearchShadowActivationDecisionV1(SearchShadowMode.TEST_ONLY, true, true,
                    "test-only shadow integration explicitly enabled");
            case SHADOW_ENABLED -> new SearchShadowActivationDecisionV1(SearchShadowMode.SHADOW_ENABLED, true, false,
                    "shadow integration explicitly enabled without production wiring authority");
        };
    }
}
