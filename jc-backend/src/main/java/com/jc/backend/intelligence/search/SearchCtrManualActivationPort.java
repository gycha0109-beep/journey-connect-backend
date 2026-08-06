package com.jc.backend.intelligence.search;

import java.time.Instant;
import java.util.Objects;

public interface SearchCtrManualActivationPort {

    Result execute(Command command);

    record Command(
            String operationId,
            Instant windowStart,
            Instant windowEnd,
            String environment,
            String policyVersion,
            Instant observedAt,
            String idempotencyKey,
            String producerBuildId) {
        public Command {
            if (operationId == null
                    || !operationId.matches("^search-ctr-manual-run:[0-9a-f]{32}$")) {
                throw new IllegalArgumentException("search CTR manual operation id is invalid");
            }
            Objects.requireNonNull(windowStart, "windowStart");
            Objects.requireNonNull(windowEnd, "windowEnd");
            Objects.requireNonNull(observedAt, "observedAt");
            new SearchCtrActivationPolicy.Window(windowStart, windowEnd);
            if (environment == null || !environment.matches("^(local|dev|test|stage)$")) {
                throw new IllegalArgumentException("search CTR manual environment is invalid");
            }
            if (!SearchCtrActivationPolicy.POLICY_VERSION.equals(policyVersion)) {
                throw new IllegalArgumentException("search CTR manual policy version is invalid");
            }
            if (!SearchCtrActivationPolicy.isProvisionalEligible(
                    new SearchCtrActivationPolicy.Window(windowStart, windowEnd), observedAt)) {
                throw new IllegalArgumentException("search CTR manual window is not provisionally eligible");
            }
            if (idempotencyKey == null
                    || !idempotencyKey.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")) {
                throw new IllegalArgumentException("search CTR manual idempotency key is invalid");
            }
            if (producerBuildId == null
                    || !producerBuildId.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")) {
                throw new IllegalArgumentException("search CTR manual producer build is invalid");
            }
        }
    }

    record Result(
            String operationId,
            SearchCtrProjectionPort.WriteStatus writeStatus,
            String projectionId,
            String projectionFingerprint,
            String predecessorProjectionId,
            String metricId,
            String metricVersion,
            Instant windowStart,
            Instant windowEnd,
            String projectionStatus,
            long eligibleExposureCount,
            long attributedExposureCount,
            Integer ctrBasisPoints,
            Instant computedAt,
            Instant sourceMaxReceivedAt) {}
}
