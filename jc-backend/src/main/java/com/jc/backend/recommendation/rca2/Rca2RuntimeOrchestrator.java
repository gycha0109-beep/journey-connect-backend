package com.jc.backend.recommendation.rca2;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class Rca2RuntimeOrchestrator {
    public static final String PRIMARY_RESPONSE_COMMITTED_BEFORE_SHADOW_SUBMISSION = "YES";
    public static final String SHADOW_TASK_PRIMARY_JOIN = "FORBIDDEN";
    public static final String SHADOW_TASK_RESPONSE_MUTATION = "FORBIDDEN";

    private final Clock clock;
    private final Rca2FeatureFlagPolicy flags;
    private final Rca2KillSwitch killSwitch;
    private final Rca2IdentityPolicy identityPolicy;
    private final Map<Rca2RuntimeContracts.Lane, Rca2CircuitBreaker> breakers;
    private final Rca2BoundedExecutor executor;
    private final Rca2CandidateAdapter adapter;
    private final Rca2Comparator comparator;
    private final Rca2Metrics metrics;
    private final Rca2Redaction redaction;
    private final Consumer<Rca2RuntimeContracts.Evidence> evidenceSink;

    public Rca2RuntimeOrchestrator(
            Clock clock,
            Rca2FeatureFlagPolicy flags,
            Rca2KillSwitch killSwitch,
            Rca2IdentityPolicy identityPolicy,
            Map<Rca2RuntimeContracts.Lane, Rca2CircuitBreaker> breakers,
            Rca2BoundedExecutor executor,
            Rca2CandidateAdapter adapter,
            Rca2Comparator comparator,
            Rca2Metrics metrics,
            Rca2Redaction redaction,
            Consumer<Rca2RuntimeContracts.Evidence> evidenceSink) {
        this.clock = Objects.requireNonNull(clock);
        this.flags = Objects.requireNonNull(flags);
        this.killSwitch = Objects.requireNonNull(killSwitch);
        this.identityPolicy = Objects.requireNonNull(identityPolicy);
        this.breakers = new EnumMap<>(breakers);
        this.executor = Objects.requireNonNull(executor);
        this.adapter = Objects.requireNonNull(adapter);
        this.comparator = Objects.requireNonNull(comparator);
        this.metrics = Objects.requireNonNull(metrics);
        this.redaction = Objects.requireNonNull(redaction);
        this.evidenceSink = Objects.requireNonNull(evidenceSink);
    }

    public Rca2RuntimeContracts.SubmissionStatus submitAfterResponseCommitted(
            Rca2RuntimeContracts.ShadowRequest request,
            boolean responseCommitted) {
        Objects.requireNonNull(request, "request");
        var lane = request.primary().lane();
        var breaker = breakers.get(lane);
        metrics.increment("shadow_request_count", lane, "received", breaker.state());
        metrics.recordMillis("primary_latency_ms", lane, "primary", breaker.state(), request.primary().primaryLatencyMillis());
        if (!responseCommitted) return Rca2RuntimeContracts.SubmissionStatus.RESPONSE_NOT_COMMITTED;
        if (killSwitch.globalKilled()) return Rca2RuntimeContracts.SubmissionStatus.GLOBAL_KILLED;
        if (killSwitch.laneKilled(lane)) return Rca2RuntimeContracts.SubmissionStatus.LANE_KILLED;
        Instant now = clock.instant();
        var flag = flags.evaluate(lane, now);
        if (!flag.enabled()) {
            return flag.reason() == Rca2FeatureFlagPolicy.Reason.TRAFFIC_ZERO
                    ? Rca2RuntimeContracts.SubmissionStatus.TRAFFIC_ZERO
                    : Rca2RuntimeContracts.SubmissionStatus.FLAG_OFF;
        }
        if (!sample(request.hashedRequestRef(), flag.trafficPercent())) return Rca2RuntimeContracts.SubmissionStatus.FLAG_OFF;
        var identity = new Rca2IdentityPolicy.Identity(request.identityRef(), request.purpose(), request.caller(),
                request.environment(), now.plusSeconds(3600), false, false, true, true);
        var identityDecision = identityPolicy.validate(identity, now);
        if (!identityDecision.allowed()) {
            metrics.increment("identity_blocked_count", lane, "identity_blocked", breaker.state());
            return Rca2RuntimeContracts.SubmissionStatus.IDENTITY_BLOCKED;
        }
        if (!breaker.permit()) {
            metrics.increment("shadow_circuit_open_count", lane, "circuit_open", breaker.state());
            return Rca2RuntimeContracts.SubmissionStatus.CIRCUIT_OPEN;
        }
        boolean accepted = executor.submit(
                () -> adapter.compute(request, Rca2RuntimeContracts.Deadline.approved()),
                completion -> complete(request, completion, breaker));
        if (!accepted) {
            metrics.increment("shadow_queue_rejected_count", lane, "queue_rejected", breaker.state());
            return Rca2RuntimeContracts.SubmissionStatus.QUEUE_REJECTED;
        }
        metrics.increment("shadow_execution_count", lane, "submitted", breaker.state());
        return Rca2RuntimeContracts.SubmissionStatus.ACCEPTED;
    }

    private void complete(Rca2RuntimeContracts.ShadowRequest request,
            Rca2BoundedExecutor.Completion<Rca2RuntimeContracts.CandidateResult> completion,
            Rca2CircuitBreaker breaker) {
        var lane = request.primary().lane();
        metrics.recordMillis("shadow_latency_ms", lane, completion.status().name().toLowerCase(), breaker.state(),
                completion.elapsedMillis());
        switch (completion.status()) {
            case TIMEOUT -> {
                metrics.increment("shadow_timeout_count", lane, "timeout", breaker.state());
                breaker.failure(true);
            }
            case EXCEPTION, CANCELLED -> {
                metrics.increment("shadow_exception_count", lane, "exception", breaker.state());
                breaker.failure(false);
            }
            case LATE_DISCARDED -> {
                metrics.increment("shadow_late_result_discard_count", lane, "late_discard", breaker.state());
                breaker.failure(true);
            }
            case SUCCESS -> handleSuccess(request, completion.value(), completion.elapsedMillis(), breaker);
        }
    }

    private void handleSuccess(Rca2RuntimeContracts.ShadowRequest request,
            Rca2RuntimeContracts.CandidateResult candidate,
            long shadowLatencyMillis,
            Rca2CircuitBreaker breaker) {
        var lane = request.primary().lane();
        var result = comparator.compare(request.primary(), candidate);
        if (result.classification() == Rca2RuntimeContracts.ComparisonClass.CHECKPOINT_MISMATCH) {
            metrics.increment("checkpoint_mismatch_count", lane, "checkpoint_mismatch", breaker.state());
        } else if (result.classification() == Rca2RuntimeContracts.ComparisonClass.LINEAGE_MISMATCH) {
            metrics.increment("lineage_mismatch_count", lane, "lineage_mismatch", breaker.state());
        } else if (result.classification() == Rca2RuntimeContracts.ComparisonClass.STALE_CANDIDATE) {
            metrics.increment("stale_candidate_count", lane, "stale_candidate", breaker.state());
        } else if (result.classification() == Rca2RuntimeContracts.ComparisonClass.RESULT_MISMATCH) {
            metrics.increment(lane == Rca2RuntimeContracts.Lane.P1 ? "p1_result_mismatch_count" : "p2_result_mismatch_count",
                    lane, "result_mismatch", breaker.state());
        }
        try {
            var evidence = redaction.verify(new Rca2RuntimeContracts.Evidence(
                    request.hashedRequestRef(), lane, Rca2RuntimeContracts.CONTRACT_VERSION,
                    Rca2RuntimeContracts.QUERY_REGISTRY_VERSION, request.primary().digest(), candidate.digest(),
                    result.classification().name(), safeCheckpoint(request.primary().checkpoint().opaqueRef()),
                    safeCheckpoint(candidate.checkpoint().opaqueRef()), candidate.lineage().fingerprint(),
                    latencyBucket(shadowLatencyMillis), latencyBucket(request.primary().primaryLatencyMillis()),
                    "none", "none", request.flagVersion(), candidate.lineage().deploymentVersion(),
                    Rca2RuntimeContracts.WORK_START_SHA));
            evidenceSink.accept(evidence);
        } catch (RuntimeException exception) {
            metrics.increment("redaction_failure_count", lane, "redaction_failure", breaker.state());
            killSwitch.failClosed();
            breaker.failure(false);
            return;
        }
        metrics.increment("shadow_success_count", lane, result.classification().name().toLowerCase(), breaker.state());
        breaker.success();
    }

    private static boolean sample(String hash, int percent) {
        if (percent <= 0) return false;
        long bucket = Long.parseLong(hash.substring(0, 8), 16);
        return bucket % 100L < percent;
    }

    private static String latencyBucket(long millis) {
        if (millis <= 10) return "le_10ms";
        if (millis <= 50) return "le_50ms";
        if (millis <= 100) return "le_100ms";
        if (millis <= 250) return "le_250ms";
        if (millis <= 500) return "le_500ms";
        return "gt_500ms";
    }

    private static String safeCheckpoint(String value) {
        return Rca2IdentityPolicy.sha256("checkpoint|" + value);
    }
}
