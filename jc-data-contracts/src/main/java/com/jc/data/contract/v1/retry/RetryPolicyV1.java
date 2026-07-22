package com.jc.data.contract.v1.retry;

import com.jc.data.contract.support.Sha256DigestV1;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RetryPolicyV1 {
    public static final String POLICY_ID = "data-projection-retry-v1";
    public static final int MAX_AUTOMATIC_RETRIES = 5;
    public static final int MAX_TOTAL_EXECUTIONS = 6;
    public static final Duration LEASE_DURATION = Duration.ofSeconds(60);
    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);
    public static final int DEFAULT_MAX_CLAIM_BATCH = 100;
    private static final Pattern WORK_REF = Pattern.compile("work:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}");
    private static final List<Duration> BASE_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12));

    public List<Duration> baseDelays() {
        return BASE_DELAYS;
    }

    public Duration scheduledDelay(String workRef, int automaticRetryNumber) {
        validateWorkRef(workRef);
        if (automaticRetryNumber < 1 || automaticRetryNumber > MAX_AUTOMATIC_RETRIES) {
            throw new IllegalArgumentException("automaticRetryNumber must be 1..5");
        }
        Duration base = BASE_DELAYS.get(automaticRetryNumber - 1);
        int jitterBasisPoints = deterministicJitterBasisPoints(workRef, automaticRetryNumber);
        long baseMillis = base.toMillis();
        long jitterMillis = Math.multiplyExact(baseMillis, jitterBasisPoints) / 10_000L;
        return Duration.ofMillis(Math.addExact(baseMillis, jitterMillis));
    }

    public int deterministicJitterBasisPoints(String workRef, int automaticRetryNumber) {
        validateWorkRef(workRef);
        if (automaticRetryNumber < 1 || automaticRetryNumber > MAX_AUTOMATIC_RETRIES) {
            throw new IllegalArgumentException("automaticRetryNumber must be 1..5");
        }
        String seed = POLICY_ID + "\u001f" + workRef + "\u001f" + automaticRetryNumber;
        String digest = Sha256DigestV1.lowercaseHex(seed.getBytes(StandardCharsets.UTF_8));
        long prefix = Long.parseUnsignedLong(digest.substring(0, 8), 16);
        return (int) (prefix % 1001L);
    }

    public RetryDecisionV1 decide(
            String workRef,
            int currentAttemptNumber,
            String failureWire,
            int consecutiveSameSignatureCount) {
        validateWorkRef(workRef);
        if (currentAttemptNumber < 1 || currentAttemptNumber > MAX_TOTAL_EXECUTIONS) {
            throw new IllegalArgumentException("currentAttemptNumber must be 1..6");
        }
        if (consecutiveSameSignatureCount < 1) {
            throw new IllegalArgumentException("consecutiveSameSignatureCount must be positive");
        }
        RetryFailureClassV1 failureClass = RetryFailureClassV1.fromWireFailClosed(failureWire);
        if (!failureClass.automaticallyRetryable()) {
            return quarantine(currentAttemptNumber, QuarantineReasonV1.fromFailure(failureClass));
        }
        if (consecutiveSameSignatureCount >= 3) {
            return quarantine(currentAttemptNumber, QuarantineReasonV1.REPEATED_DETERMINISTIC_FAILURE);
        }
        if (currentAttemptNumber >= MAX_TOTAL_EXECUTIONS) {
            return quarantine(currentAttemptNumber, QuarantineReasonV1.RETRY_EXHAUSTED);
        }
        int automaticRetryNumber = currentAttemptNumber;
        return new RetryDecisionV1(
                RetryDecisionV1.Decision.RETRY,
                currentAttemptNumber,
                currentAttemptNumber + 1,
                scheduledDelay(workRef, automaticRetryNumber),
                null);
    }

    private static RetryDecisionV1 quarantine(int currentAttemptNumber, QuarantineReasonV1 reason) {
        return new RetryDecisionV1(
                RetryDecisionV1.Decision.QUARANTINE,
                currentAttemptNumber,
                0,
                Duration.ZERO,
                reason);
    }

    private static void validateWorkRef(String workRef) {
        Objects.requireNonNull(workRef, "workRef");
        if (!WORK_REF.matcher(workRef).matches()) {
            throw new IllegalArgumentException("workRef must use work:<opaque-id>");
        }
    }
}
