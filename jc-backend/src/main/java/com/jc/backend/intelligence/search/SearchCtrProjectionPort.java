package com.jc.backend.intelligence.search;

import java.time.Instant;

public interface SearchCtrProjectionPort {

    WriteResult write(WriteCommand command);

    record WriteCommand(
            Instant windowStart,
            Instant windowEnd,
            String expectedPredecessorProjectionId,
            String idempotencyKey,
            String producerBuildId) {
        public WriteCommand {
            if (windowStart == null || windowEnd == null || !windowStart.isBefore(windowEnd)) {
                throw new IllegalArgumentException("search CTR projection window is invalid");
            }
            if (expectedPredecessorProjectionId != null
                    && !expectedPredecessorProjectionId.matches(
                            "^search-ctr-projection:[0-9a-f]{32}$")) {
                throw new IllegalArgumentException("search CTR expected predecessor is invalid");
            }
            if (idempotencyKey == null
                    || !idempotencyKey.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")) {
                throw new IllegalArgumentException("search CTR idempotency key is invalid");
            }
            if (producerBuildId == null
                    || !producerBuildId.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")) {
                throw new IllegalArgumentException("search CTR producer build is invalid");
            }
        }
    }

    enum WriteStatus {
        STORED,
        DUPLICATE,
        IDEMPOTENCY_CONFLICT,
        PREDECESSOR_CONFLICT
    }

    record WriteResult(
            WriteStatus status,
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
