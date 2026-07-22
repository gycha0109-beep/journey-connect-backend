package com.jc.data.contract.v1.retry;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record LeaseBoundaryV1(
        UUID workId,
        String workerRef,
        UUID claimToken,
        int attemptNumber,
        Instant claimedAt,
        Instant leaseExpiresAt) {
    private static final Pattern WORKER_REF = Pattern.compile("worker:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}");

    public LeaseBoundaryV1 {
        Objects.requireNonNull(workId, "workId");
        Objects.requireNonNull(workerRef, "workerRef");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        if (!WORKER_REF.matcher(workerRef).matches()) {
            throw new IllegalArgumentException("workerRef malformed");
        }
        if (attemptNumber < 1 || attemptNumber > RetryPolicyV1.MAX_TOTAL_EXECUTIONS) {
            throw new IllegalArgumentException("attemptNumber out of range");
        }
        if (!leaseExpiresAt.equals(claimedAt.plus(RetryPolicyV1.LEASE_DURATION))) {
            throw new IllegalArgumentException("lease must be exactly 60 seconds");
        }
    }

    public boolean isExpiredAt(Instant referenceTime) {
        Objects.requireNonNull(referenceTime, "referenceTime");
        return !referenceTime.isBefore(leaseExpiresAt);
    }
}
