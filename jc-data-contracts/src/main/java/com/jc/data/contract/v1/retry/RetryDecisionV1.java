package com.jc.data.contract.v1.retry;

import java.time.Duration;
import java.util.Objects;

public record RetryDecisionV1(
        Decision decision,
        int currentAttemptNumber,
        int nextAttemptNumber,
        Duration delay,
        QuarantineReasonV1 quarantineReason) {

    public RetryDecisionV1 {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(delay, "delay");
        if (currentAttemptNumber < 1 || currentAttemptNumber > RetryPolicyV1.MAX_TOTAL_EXECUTIONS) {
            throw new IllegalArgumentException("current attempt out of range");
        }
        if (decision == Decision.RETRY) {
            if (nextAttemptNumber != currentAttemptNumber + 1 || quarantineReason != null) {
                throw new IllegalArgumentException("retry decision fields are inconsistent");
            }
        } else if (nextAttemptNumber != 0 || !delay.isZero() || quarantineReason == null) {
            throw new IllegalArgumentException("quarantine decision fields are inconsistent");
        }
    }

    public enum Decision {
        RETRY,
        QUARANTINE
    }
}
