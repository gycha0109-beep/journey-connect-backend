package com.jc.backend.search.shadow.production;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public record ProductionSearchShadowRuntimeConfig(
        boolean enabled,
        boolean killSwitchActive,
        int configuredSamplingBps,
        int effectiveSamplingBps,
        Set<String> allowlistHashes,
        String activationApprovalRef,
        String activationApproverRef,
        String activationExecutorRef,
        String rollbackOwnerRef,
        String metricVerificationRef,
        Instant activationWindowStart,
        Instant activationWindowEnd,
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

    public boolean operationalInputsPresent() {
        return activationApprovalRef != null
                && activationApproverRef != null
                && activationExecutorRef != null
                && rollbackOwnerRef != null
                && metricVerificationRef != null
                && activationWindowStart != null
                && activationWindowEnd != null;
    }

    public boolean activationWindowAllows(Instant now) {
        return operationalInputsPresent()
                && !now.isBefore(activationWindowStart)
                && now.isBefore(activationWindowEnd);
    }

    public boolean dispatchConfigured() {
        return enabled
                && !killSwitchActive
                && effectiveSamplingBps > 0
                && hasCohort()
                && operationalInputsPresent();
    }
}
