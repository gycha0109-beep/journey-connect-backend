package com.jc.intelligence.production.search.v1;

import java.time.Duration;
import java.util.Objects;

public record ProductionShadowResourcePolicyV1(
        int coreConcurrency,
        int maxConcurrency,
        int queueCapacity,
        Duration queueWaitCeiling,
        Duration runtimeTimeout,
        Duration hardCancellationTimeout,
        int circuitFailureThreshold,
        Duration circuitRecoveryPeriod,
        int maximumSampleBasisPoints,
        int maximumCandidateCount,
        Duration shutdownGracePeriod,
        String approvalStatus) {
    public static final String PROVISIONAL = "PROVISIONAL_NOT_APPROVED";
    public static final String INITIAL_PILOT_APPROVED = "APPROVED_INITIAL_PILOT";

    public ProductionShadowResourcePolicyV1 {
        if (coreConcurrency < 1 || maxConcurrency < coreConcurrency || maxConcurrency > 32) {
            throw new IllegalArgumentException("concurrency invalid");
        }
        if (queueCapacity < 1 || queueCapacity > 1024) {
            throw new IllegalArgumentException("queueCapacity invalid");
        }
        Objects.requireNonNull(queueWaitCeiling, "queueWaitCeiling");
        Objects.requireNonNull(runtimeTimeout, "runtimeTimeout");
        Objects.requireNonNull(hardCancellationTimeout, "hardCancellationTimeout");
        Objects.requireNonNull(circuitRecoveryPeriod, "circuitRecoveryPeriod");
        Objects.requireNonNull(shutdownGracePeriod, "shutdownGracePeriod");
        if (queueWaitCeiling.isNegative()
                || runtimeTimeout.isZero()
                || runtimeTimeout.isNegative()
                || hardCancellationTimeout.compareTo(runtimeTimeout) < 0) {
            throw new IllegalArgumentException("timeout invalid");
        }
        if (circuitFailureThreshold < 1 || circuitFailureThreshold > 1000) {
            throw new IllegalArgumentException("circuit threshold invalid");
        }
        if (maximumSampleBasisPoints < 0 || maximumSampleBasisPoints > 1000) {
            throw new IllegalArgumentException("sample ceiling invalid");
        }
        if (maximumCandidateCount < 1 || maximumCandidateCount > 1000) {
            throw new IllegalArgumentException("candidate boundary invalid");
        }
        if (!PROVISIONAL.equals(approvalStatus) && !INITIAL_PILOT_APPROVED.equals(approvalStatus)) {
            throw new IllegalArgumentException("resource approval status invalid");
        }
        if (INITIAL_PILOT_APPROVED.equals(approvalStatus)) {
            if (coreConcurrency != 1 || maxConcurrency != 2 || queueCapacity != 8
                    || !runtimeTimeout.equals(Duration.ofMillis(200))
                    || !hardCancellationTimeout.equals(Duration.ofMillis(300))
                    || maximumSampleBasisPoints != 10 || maximumCandidateCount != 100) {
                throw new IllegalArgumentException("approved initial pilot bounds must remain exact");
            }
        }
    }

    public static ProductionShadowResourcePolicyV1 provisional() {
        return new ProductionShadowResourcePolicyV1(
                1, 2, 8, Duration.ofMillis(10), Duration.ofMillis(200), Duration.ofMillis(250),
                5, Duration.ofSeconds(30), 100, 100, Duration.ofSeconds(2), PROVISIONAL);
    }

    public static ProductionShadowResourcePolicyV1 approvedInitialPilot() {
        return new ProductionShadowResourcePolicyV1(
                1, 2, 8, Duration.ofMillis(10), Duration.ofMillis(200), Duration.ofMillis(300),
                5, Duration.ofSeconds(30), 10, 100, Duration.ofSeconds(2), INITIAL_PILOT_APPROVED);
    }
}
