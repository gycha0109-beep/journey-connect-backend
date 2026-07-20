package com.jc.intelligence.wiring.search.v1;

public record SearchShadowActivationInputsV1(
        boolean runtimeInputProviderRegistered,
        boolean executorRegistered,
        boolean comparisonLoggerRegistered) { }
