package com.jc.backend.recommendation.rca2;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Rca2Metrics {
    public static final String[] REQUIRED = {
        "shadow_request_count", "shadow_execution_count", "shadow_success_count", "shadow_timeout_count",
        "shadow_exception_count", "shadow_circuit_open_count", "shadow_queue_rejected_count",
        "shadow_late_result_discard_count", "shadow_latency_ms", "primary_latency_ms",
        "p1_result_mismatch_count", "p2_result_mismatch_count", "checkpoint_mismatch_count",
        "lineage_mismatch_count", "stale_candidate_count", "identity_blocked_count", "redaction_failure_count",
        "shadow_endpoint_validation_total", "shadow_endpoint_blocked_total",
        "shadow_credential_unavailable_total", "shadow_credential_refresh_total",
        "shadow_credential_refresh_failure_total", "shadow_allowlist_lookup_total",
        "shadow_allowlist_denied_total", "shadow_cohort_selected_total",
        "shadow_cohort_skipped_total", "shadow_candidate_invocation_blocked_total"
    };
    public static final Set<String> ALLOWED_LABELS = Set.of("environment", "lane", "result_class", "breaker_state");
    private static final Set<String> HISTOGRAMS = Set.of("shadow_latency_ms", "primary_latency_ms");
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final MeterRegistry registry;
    private final Rca2Op2Telemetry op2;

    public Rca2Metrics(MeterRegistry registry) {
        this.registry = registry;
        this.op2 = new Rca2Op2Telemetry(registry);
    }
    public static Rca2Metrics inMemory() { return new Rca2Metrics(null); }
    public Rca2Op2Telemetry op2() { return op2; }
    public void bindRuntime(Rca2BoundedExecutor executor, Rca2KillSwitch killSwitch) { op2.bindRuntime(executor, killSwitch); }

    public void increment(String metric, Rca2RuntimeContracts.Lane lane, String resultClass,
            Rca2RuntimeContracts.BreakerState breakerState) {
        require(metric, false);
        String key = key(metric, lane, resultClass, breakerState);
        counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        if (registry != null) registry.counter("rca2." + metric, tags(lane, resultClass, breakerState)).increment();
        mirrorCounter(metric, lane, resultClass);
    }

    public void recordMillis(String metric, Rca2RuntimeContracts.Lane lane, String resultClass,
            Rca2RuntimeContracts.BreakerState breakerState, long millis) {
        require(metric, true);
        if (millis < 0) throw new IllegalArgumentException("millis must be nonnegative");
        counters.computeIfAbsent(key(metric, lane, resultClass, breakerState), ignored -> new AtomicLong())
                .addAndGet(millis);
        if (registry != null) Timer.builder("rca2." + metric)
                .tags(tags(lane, resultClass, breakerState))
                .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(25), Duration.ofMillis(50),
                        Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(1_000))
                .register(registry).record(Duration.ofMillis(millis));
        if (metric.equals("shadow_latency_ms")) {
            op2.recordMillis("shadow_total_duration_milliseconds", lane, resultClass, millis);
        }
    }

    public long total(String metric) {
        return counters.entrySet().stream().filter(entry -> entry.getKey().startsWith(metric + "|"))
                .mapToLong(entry -> entry.getValue().get()).sum();
    }

    public Map<String, String> definitions() {
        Map<String, String> definitions = new LinkedHashMap<>();
        for (String metric : REQUIRED) definitions.put(metric, HISTOGRAMS.contains(metric) ? "HISTOGRAM" : "COUNTER");
        definitions.put("shadow_write_attempt_blocked_count", "COUNTER");
        definitions.put("shadow_event_attempt_blocked_count", "COUNTER");
        definitions.put("shadow_response_mutation_blocked_count", "COUNTER");
        op2.definitions().forEach((name, descriptor) -> definitions.put(name, descriptor.type().name()));
        return Map.copyOf(definitions);
    }

    private void mirrorCounter(String metric, Rca2RuntimeContracts.Lane lane, String resultClass) {
        switch (metric) {
            case "shadow_request_count" -> op2.increment("shadow_submission_total", lane, resultClass);
            case "shadow_execution_count" -> op2.increment("shadow_execution_started_total", lane, resultClass);
            case "shadow_timeout_count" -> op2.increment("shadow_timeout_total", lane, resultClass);
            case "shadow_exception_count" -> op2.increment("shadow_exception_total", lane, resultClass);
            case "shadow_queue_rejected_count" -> op2.increment("shadow_queue_rejection_total", lane, resultClass);
            case "shadow_late_result_discard_count" -> op2.increment("shadow_late_discard_total", lane, resultClass);
            case "lineage_mismatch_count" -> op2.increment("shadow_lineage_mismatch_total", lane, resultClass);
            case "p1_result_mismatch_count" -> op2.increment("shadow_p1_unexpected_mismatch_total", lane, resultClass);
            case "p2_result_mismatch_count" -> op2.increment("shadow_p2_unexpected_mismatch_total", lane, resultClass);
            case "redaction_failure_count" -> op2.increment("shadow_redaction_failure_total", lane, resultClass);
            default -> { }
        }
    }

    private static void require(String metric, boolean histogram) {
        boolean required = java.util.Arrays.asList(REQUIRED).contains(metric)
                || metric.equals("shadow_write_attempt_blocked_count")
                || metric.equals("shadow_event_attempt_blocked_count")
                || metric.equals("shadow_response_mutation_blocked_count");
        if (!required) throw new IllegalArgumentException("unregistered metric: " + metric);
        if (histogram != HISTOGRAMS.contains(metric)) throw new IllegalArgumentException("metric type mismatch: " + metric);
    }

    private static String key(String metric, Rca2RuntimeContracts.Lane lane, String resultClass,
            Rca2RuntimeContracts.BreakerState breakerState) {
        return metric + "|" + lane.name() + "|" + safeResultClass(resultClass) + "|" + breakerState.name();
    }

    private static String[] tags(Rca2RuntimeContracts.Lane lane, String resultClass,
            Rca2RuntimeContracts.BreakerState breakerState) {
        return new String[] {"environment", "isolated_nonproduction", "lane", lane.name().toLowerCase(),
                "result_class", safeResultClass(resultClass), "breaker_state", breakerState.name().toLowerCase()};
    }

    private static String safeResultClass(String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,40}")) return "unknown";
        return value;
    }
}
