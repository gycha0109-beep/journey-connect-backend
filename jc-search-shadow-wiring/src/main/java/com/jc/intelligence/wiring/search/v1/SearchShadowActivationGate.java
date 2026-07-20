package com.jc.intelligence.wiring.search.v1;

import java.util.Objects;

public final class SearchShadowActivationGate {
    public SearchShadowActivationDecisionV1 decide(
            SearchShadowWiringConfigV1 config, SearchShadowActivationInputsV1 inputs) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(inputs, "inputs");
        if (config.mode() == SearchShadowWiringMode.DISABLED) return new SearchShadowActivationDecisionV1(false, "disabled");
        if (config.mode() == SearchShadowWiringMode.SHADOW_CANDIDATE) return new SearchShadowActivationDecisionV1(false, "production_activation_hold");
        if (!config.explicitAllow()) return new SearchShadowActivationDecisionV1(false, "explicit_allow_missing");
        if (!SearchShadowWiringConfigV1.TEST_PROFILE.equals(config.activeProfile())
                && !SearchShadowWiringConfigV1.STAGE_PROFILE.equals(config.activeProfile())) {
            return new SearchShadowActivationDecisionV1(false, "profile_blocked");
        }
        if (!inputs.runtimeInputProviderRegistered()) return new SearchShadowActivationDecisionV1(false, "runtime_input_provider_missing");
        if (!inputs.executorRegistered()) return new SearchShadowActivationDecisionV1(false, "executor_missing");
        if (!inputs.comparisonLoggerRegistered()) return new SearchShadowActivationDecisionV1(false, "comparison_logger_missing");
        return new SearchShadowActivationDecisionV1(true, "activated_test_only");
    }
}
