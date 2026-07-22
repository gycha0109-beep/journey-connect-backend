package com.jc.data.contract;

import com.jc.data.contract.v1.retry.FailureSignatureV1;
import com.jc.data.contract.v1.retry.LeaseBoundaryV1;
import com.jc.data.contract.v1.retry.ProcessingOutcomeV1;
import com.jc.data.contract.v1.retry.QuarantineReasonV1;
import com.jc.data.contract.v1.retry.RetryDecisionV1;
import com.jc.data.contract.v1.retry.RetryFailureClassV1;
import com.jc.data.contract.v1.retry.RetryPolicyV1;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public final class Dp3RetryPolicyContractTest {
    private static int assertions;

    private Dp3RetryPolicyContractTest() {
    }

    public static void main(String[] args) {
        RetryPolicyV1 policy = new RetryPolicyV1();
        check(RetryPolicyV1.POLICY_ID.equals("data-projection-retry-v1"), "policy ID");
        check(RetryPolicyV1.MAX_AUTOMATIC_RETRIES == 5, "max retries");
        check(RetryPolicyV1.MAX_TOTAL_EXECUTIONS == 6, "max executions");
        check(RetryPolicyV1.LEASE_DURATION.equals(Duration.ofSeconds(60)), "lease duration");
        check(RetryPolicyV1.HEARTBEAT_INTERVAL.equals(Duration.ofSeconds(20)), "heartbeat interval");
        check(RetryPolicyV1.DEFAULT_MAX_CLAIM_BATCH == 100, "claim batch");
        check(policy.baseDelays().equals(java.util.List.of(
                Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
                Duration.ofHours(2), Duration.ofHours(12))), "base delay sequence");

        for (int retry = 1; retry <= 5; retry++) {
            Duration first = policy.scheduledDelay("work:fixture-a", retry);
            Duration second = policy.scheduledDelay("work:fixture-a", retry);
            Duration base = policy.baseDelays().get(retry - 1);
            check(first.equals(second), "deterministic delay " + retry);
            check(!first.minus(base).isNegative(), "jitter lower bound " + retry);
            check(first.compareTo(base.plus(base.dividedBy(10))) <= 0, "jitter upper bound " + retry);
            int basisPoints = policy.deterministicJitterBasisPoints("work:fixture-a", retry);
            check(basisPoints >= 0 && basisPoints <= 1000, "jitter basis points " + retry);
        }

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            Duration changedDefaults = policy.scheduledDelay("work:fixture-a", 3);
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            check(changedDefaults.equals(policy.scheduledDelay("work:fixture-a", 3)), "locale/timezone independent");
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }

        for (RetryFailureClassV1 failureClass : RetryFailureClassV1.values()) {
            check(failureClass.wireValue().matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"),
                    "failure wire snake case " + failureClass.wireValue());
            check(RetryFailureClassV1.fromWire(failureClass.wireValue()).orElseThrow() == failureClass,
                    "failure wire round trip " + failureClass.wireValue());
        }
        check(RetryFailureClassV1.fromWireFailClosed("new_unknown_failure")
                == RetryFailureClassV1.UNCLASSIFIED_FAILURE, "unknown fail closed");

        for (int attempt = 1; attempt <= 5; attempt++) {
            RetryDecisionV1 decision = policy.decide("work:retryable", attempt, "dependency_unavailable", 1);
            check(decision.decision() == RetryDecisionV1.Decision.RETRY, "retryable decision " + attempt);
            check(decision.nextAttemptNumber() == attempt + 1, "next attempt " + attempt);
            check(!decision.delay().isZero(), "retry delay nonzero " + attempt);
        }
        RetryDecisionV1 exhausted = policy.decide("work:retryable", 6, "dependency_unavailable", 1);
        check(exhausted.decision() == RetryDecisionV1.Decision.QUARANTINE, "exhausted quarantine");
        check(exhausted.quarantineReason() == QuarantineReasonV1.RETRY_EXHAUSTED, "exhausted reason");

        RetryDecisionV1 repeated = policy.decide("work:retryable", 3, "serialization_failure", 3);
        check(repeated.quarantineReason() == QuarantineReasonV1.REPEATED_DETERMINISTIC_FAILURE,
                "three repeated signatures quarantine early");

        for (RetryFailureClassV1 failureClass : RetryFailureClassV1.values()) {
            if (!failureClass.automaticallyRetryable()) {
                RetryDecisionV1 decision = policy.decide("work:nonretryable", 1, failureClass.wireValue(), 1);
                check(decision.decision() == RetryDecisionV1.Decision.QUARANTINE,
                        "nonretryable quarantined " + failureClass.wireValue());
            }
        }
        RetryDecisionV1 unknown = policy.decide("work:unknown", 1, "brand_new_failure", 1);
        check(unknown.quarantineReason() == QuarantineReasonV1.UNCLASSIFIED_FAILURE,
                "unknown reason unclassified");

        String signature = FailureSignatureV1.normalized(
                RetryFailureClassV1.DEPENDENCY_UNAVAILABLE, "PROVIDER_TIMEOUT", "provider:maps-v1");
        check(signature.matches("[0-9a-f]{64}"), "failure signature lowercase sha256");
        check(signature.equals(FailureSignatureV1.normalized(
                RetryFailureClassV1.DEPENDENCY_UNAVAILABLE, " provider_timeout ", " PROVIDER:MAPS-V1 ")),
                "failure signature normalized");
        expectFailure(() -> FailureSignatureV1.normalized(
                RetryFailureClassV1.DEPENDENCY_UNAVAILABLE, "raw error message", "provider:maps-v1"));

        Set<String> quarantineWires = new HashSet<>();
        for (QuarantineReasonV1 reason : QuarantineReasonV1.values()) {
            check(reason.wireValue().matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"),
                    "quarantine snake case " + reason.wireValue());
            check(quarantineWires.add(reason.wireValue()), "quarantine wires unique");
            check(QuarantineReasonV1.fromWire(reason.wireValue()).orElseThrow() == reason,
                    "quarantine round trip");
        }

        for (ProcessingOutcomeV1 outcome : ProcessingOutcomeV1.values()) {
            check(outcome.wireValue().matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"),
                    "outcome snake case " + outcome.wireValue());
        }

        Instant claimedAt = Instant.parse("2026-07-22T00:00:00Z");
        LeaseBoundaryV1 lease = new LeaseBoundaryV1(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                "worker:fixture-a",
                UUID.fromString("00000000-0000-4000-8000-000000000002"),
                1,
                claimedAt,
                claimedAt.plusSeconds(60));
        check(!lease.isExpiredAt(claimedAt.plusSeconds(59)), "lease active before expiry");
        check(lease.isExpiredAt(claimedAt.plusSeconds(60)), "lease expired at boundary");
        expectFailure(() -> new LeaseBoundaryV1(
                lease.workId(), "worker:fixture-a", lease.claimToken(), 1,
                claimedAt, claimedAt.plusSeconds(61)));
        expectFailure(() -> policy.scheduledDelay("bad", 1));
        expectFailure(() -> policy.scheduledDelay("work:fixture", 6));

        System.out.println("DP-3 retry policy checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(Runnable action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("expected failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
