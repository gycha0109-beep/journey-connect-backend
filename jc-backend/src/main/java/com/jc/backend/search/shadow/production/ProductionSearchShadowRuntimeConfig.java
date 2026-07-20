package com.jc.backend.search.shadow.production;

import java.time.Duration;
import java.util.Set;

public record ProductionSearchShadowRuntimeConfig(
        boolean enabled,
        boolean killSwitchActive,
        int configuredSamplingBps,
        int effectiveSamplingBps,
        Set<String> allowlistHashes,
        int maximumCandidateCount,
        int coreConcurrency,
        int maximumConcurrency,
        int queueCapacity,
        Duration runtimeTimeout,
        Duration hardTimeout) {
    public ProductionSearchShadowRuntimeConfig {
        allowlistHashes = Set.copyOf(allowlistHashes);
    }

    public boolean hasCohort() {
        return !allowlistHashes.isEmpty();
    }

    public boolean dispatchConfigured() {
        return enabled && !killSwitchActive && effectiveSamplingBps > 0 && hasCohort();
    }
}
