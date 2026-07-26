package com.jc.backend.recommendation.rca2;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** OP-2 canonical observability registry. Dynamic labels are bounded and never contain identity or endpoint material. */
public final class Rca2Op2Telemetry {
    public enum Type { COUNTER, GAUGE, HISTOGRAM }

    public record Descriptor(Type type, String unit, String owner, Set<String> allowedLabels,
            int cardinalityLimit, String retention, String alertDependency, String redactionPolicy) {
        public Descriptor {
            Objects.requireNonNull(type);
            unit = required(unit);
            owner = required(owner);
            allowedLabels = Set.copyOf(allowedLabels);
            if (cardinalityLimit < 1) throw new IllegalArgumentException("cardinalityLimit must be positive");
            retention = required(retention);
            alertDependency = required(alertDependency);
            redactionPolicy = required(redactionPolicy);
        }
    }

    public static final List<String> AUTHORITATIVE_METRICS = List.of(
            "traffic_selection_evaluated_total", "traffic_selection_selected_total", "stable_hash_cohort_bucket",
            "shadow_submission_total", "shadow_execution_started_total", "shadow_execution_completed_total",
            "shadow_timeout_total", "shadow_exception_total", "shadow_queue_rejection_total",
            "shadow_late_discard_total", "shadow_cancellation_total", "shadow_executor_active",
            "shadow_executor_queue_depth", "shadow_task_age_milliseconds", "shadow_total_duration_milliseconds",
            "shadow_checkpoint_lag_seconds", "shadow_lineage_mismatch_total",
            "shadow_p1_expected_protected_gap_total", "shadow_p1_unexpected_mismatch_total",
            "shadow_p2_migration_gap_total", "shadow_p2_unexpected_mismatch_total",
            "shadow_redaction_failure_total", "shadow_response_mutation_total", "shadow_database_write_total",
            "shadow_event_emission_total", "shadow_production_route_detection_total",
            "shadow_authority_mismatch_total");

    public static final List<String> BACKLOG_METRICS = List.of(
            "traffic_selected_count", "traffic_skipped_count", "executor_active_count", "executor_queue_depth",
            "shadow_task_age_ms", "shadow_cancelled_count", "checkpoint_lag_ms");

    public static final Set<String> SAFETY_EXTENSION_METRICS = Set.of(
            "shadow_cache_write_total", "shadow_notification_emission_total",
            "shadow_production_identity_detection_total", "traffic_ceiling_violation_total",
            "credential_scope_violation_total", "kill_switch_activation_total");

    private static final String PREFIX = "rca2.op2.";
    private final MeterRegistry registry;
    private final Map<String, Descriptor> descriptors;
    private final Map<String, AtomicLong> totals = new ConcurrentHashMap<>();
    private final Map<Rca2RuntimeContracts.Lane, AtomicLong> cohortBuckets =
            new EnumMap<>(Rca2RuntimeContracts.Lane.class);
    private final AtomicBoolean runtimeBound = new AtomicBoolean(false);

    public Rca2Op2Telemetry(MeterRegistry registry) {
        this.registry = registry;
        this.descriptors = definitionsInternal();
        for (var lane : Rca2RuntimeContracts.Lane.values()) cohortBuckets.put(lane, new AtomicLong(-1));
    }

    public Map<String, Descriptor> definitions() { return descriptors; }

    public void bindRuntime(Rca2BoundedExecutor executor, Rca2KillSwitch killSwitch) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(killSwitch);
        if (registry == null || !runtimeBound.compareAndSet(false, true)) return;
        registerGauge("shadow_executor_active", executor, value -> value.activeCount(), "lane", "all");
        registerGauge("executor_active_count", executor, value -> value.activeCount(), "lane", "all");
        registerGauge("shadow_executor_queue_depth", executor, value -> value.queueDepth(), "lane", "all");
        registerGauge("executor_queue_depth", executor, value -> value.queueDepth(), "lane", "all");
        for (var lane : Rca2RuntimeContracts.Lane.values()) {
            Gauge.builder(PREFIX + "stable_hash_cohort_bucket", cohortBuckets.get(lane), AtomicLong::get)
                    .tag("stage", "stage_1").tag("lane", lane.name().toLowerCase()).register(registry);
        }
        Gauge.builder(PREFIX + "global_kill_switch_state", killSwitch, value -> value.globalKilled() ? 1 : 0)
                .register(registry);
        Gauge.builder(PREFIX + "p1_lane_kill_switch_state", killSwitch,
                value -> value.laneKilled(Rca2RuntimeContracts.Lane.P1) ? 1 : 0).register(registry);
        Gauge.builder(PREFIX + "p2_lane_kill_switch_state", killSwitch,
                value -> value.laneKilled(Rca2RuntimeContracts.Lane.P2) ? 1 : 0).register(registry);
    }

    public void increment(String metric, Rca2RuntimeContracts.Lane lane, String dimension) {
        Descriptor descriptor = require(metric, Type.COUNTER);
        String safe = safeDimension(dimension);
        totals.computeIfAbsent(key(metric, lane, safe), ignored -> new AtomicLong()).incrementAndGet();
        if (registry != null) registry.counter(PREFIX + metric, tags(metric, descriptor, lane, safe)).increment();
    }

    public void recordMillis(String metric, Rca2RuntimeContracts.Lane lane, String dimension, long millis) {
        Descriptor descriptor = require(metric, Type.HISTOGRAM);
        if (millis < 0) throw new IllegalArgumentException("millis must be nonnegative");
        String safe = safeDimension(dimension);
        totals.computeIfAbsent(key(metric, lane, safe), ignored -> new AtomicLong()).addAndGet(millis);
        if (registry != null) {
            Timer.builder(PREFIX + metric).tags(tags(metric, descriptor, lane, safe))
                    .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100),
                            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(1_000))
                    .register(registry).record(Duration.ofMillis(millis));
        }
    }

    public void recordTrafficEvaluated(Rca2RuntimeContracts.Lane lane, String result) {
        increment("traffic_selection_evaluated_total", lane, result);
    }

    public void recordTrafficSelected(Rca2RuntimeContracts.Lane lane) {
        increment("traffic_selection_selected_total", lane, "selected");
        increment("traffic_selected_count", lane, "selected");
    }

    public void recordTrafficSkipped(Rca2RuntimeContracts.Lane lane, String reason) {
        increment("traffic_skipped_count", lane, reason);
    }

    public void recordCohortBucket(Rca2RuntimeContracts.Lane lane, int bucket) {
        if (bucket < 0 || bucket > 99) return;
        cohortBuckets.get(Objects.requireNonNull(lane)).set(bucket);
    }

    public long total(String metric) {
        return totals.entrySet().stream().filter(entry -> entry.getKey().startsWith(metric + "|"))
                .mapToLong(entry -> entry.getValue().get()).sum();
    }

    private <T> void registerGauge(String metric, T target, java.util.function.ToDoubleFunction<T> function,
            String label, String value) {
        require(metric, Type.GAUGE);
        Gauge.builder(PREFIX + metric, target, function).tag(label, value).register(registry);
    }

    private Descriptor require(String metric, Type type) {
        Descriptor descriptor = descriptors.get(metric);
        if (descriptor == null) throw new IllegalArgumentException("unregistered OP-2 metric: " + metric);
        if (descriptor.type() != type) throw new IllegalArgumentException("metric type mismatch: " + metric);
        return descriptor;
    }

    private static String[] tags(String metric, Descriptor descriptor, Rca2RuntimeContracts.Lane lane, String value) {
        List<String> tags = new ArrayList<>();
        if (descriptor.allowedLabels().contains("lane")) {
            tags.add("lane"); tags.add(lane == null ? "unknown" : lane.name().toLowerCase());
        }
        if (descriptor.allowedLabels().contains("stage")) {
            tags.add("stage"); tags.add("stage_1");
        }
        String dynamic = dynamicLabel(descriptor.allowedLabels());
        if (dynamic != null) { tags.add(dynamic); tags.add(value); }
        return tags.toArray(String[]::new);
    }

    private static String dynamicLabel(Set<String> labels) {
        for (String candidate : List.of("result", "class", "reason", "gap_class", "mismatch_class")) {
            if (labels.contains(candidate)) return candidate;
        }
        return null;
    }

    private static String safeDimension(String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,48}")) return "unknown";
        return value;
    }

    private static String key(String metric, Rca2RuntimeContracts.Lane lane, String value) {
        return metric + "|" + (lane == null ? "UNKNOWN" : lane.name()) + "|" + value;
    }

    private static Map<String, Descriptor> definitionsInternal() {
        Map<String, Descriptor> result = new LinkedHashMap<>();
        add(result, "traffic_selection_evaluated_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane","stage","result"), "critical-warning");
        add(result, "traffic_selection_selected_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane","stage"), "traffic-ceiling");
        add(result, "stable_hash_cohort_bucket", Type.GAUGE, "bucket", "OPERATIONS", Set.of("stage"), "traffic-ceiling");
        add(result, "shadow_submission_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane","result"), "rate-denominator");
        add(result, "shadow_execution_started_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane"), "executor");
        add(result, "shadow_execution_completed_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane","result"), "executor");
        add(result, "shadow_timeout_total", Type.COUNTER, "count", "RELIABILITY", Set.of("lane"), "timeout-rate");
        add(result, "shadow_exception_total", Type.COUNTER, "count", "RELIABILITY", Set.of("lane","class"), "exception-rate");
        add(result, "shadow_queue_rejection_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane"), "queue-rejection-rate");
        add(result, "shadow_late_discard_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane"), "late-discard-rate");
        add(result, "shadow_cancellation_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane","reason"), "cancellation");
        add(result, "shadow_executor_active", Type.GAUGE, "count", "OPERATIONS", Set.of("lane"), "executor-saturation");
        add(result, "shadow_executor_queue_depth", Type.GAUGE, "count", "OPERATIONS", Set.of("lane"), "executor-saturation");
        add(result, "shadow_task_age_milliseconds", Type.HISTOGRAM, "milliseconds", "OPERATIONS", Set.of("lane"), "task-age");
        add(result, "shadow_total_duration_milliseconds", Type.HISTOGRAM, "milliseconds", "OPERATIONS", Set.of("lane","result"), "latency");
        add(result, "shadow_checkpoint_lag_seconds", Type.HISTOGRAM, "seconds", "DATA", Set.of("lane"), "checkpoint-lag");
        add(result, "shadow_lineage_mismatch_total", Type.COUNTER, "count", "DATA", Set.of("lane","class"), "lineage-mismatch");
        add(result, "shadow_p1_expected_protected_gap_total", Type.COUNTER, "count", "INTELLIGENCE", Set.of("gap_class"), "p1-gap");
        add(result, "shadow_p1_unexpected_mismatch_total", Type.COUNTER, "count", "INTELLIGENCE", Set.of("mismatch_class"), "p1-mismatch");
        add(result, "shadow_p2_migration_gap_total", Type.COUNTER, "count", "RELIABILITY", Set.of("gap_class"), "p2-gap");
        add(result, "shadow_p2_unexpected_mismatch_total", Type.COUNTER, "count", "RELIABILITY", Set.of("mismatch_class"), "p2-mismatch");
        add(result, "shadow_redaction_failure_total", Type.COUNTER, "count", "PRIVACY_SECURITY", Set.of("lane"), "critical-zero");
        add(result, "shadow_response_mutation_total", Type.COUNTER, "count", "RELIABILITY", Set.of("lane"), "critical-zero");
        add(result, "shadow_database_write_total", Type.COUNTER, "count", "DATA", Set.of("lane"), "critical-zero");
        add(result, "shadow_event_emission_total", Type.COUNTER, "count", "DATA", Set.of("lane"), "critical-zero");
        add(result, "shadow_production_route_detection_total", Type.COUNTER, "count", "OPERATIONS", Set.of("lane"), "critical-zero");
        add(result, "shadow_authority_mismatch_total", Type.COUNTER, "count", "SYSTEM_COORDINATION", Set.of("lane"), "critical-zero");
        add(result, "traffic_selected_count", Type.COUNTER, "count", "OPERATIONS", Set.of("lane"), "traffic-ceiling");
        add(result, "traffic_skipped_count", Type.COUNTER, "count", "OPERATIONS", Set.of("lane","reason"), "traffic-selection");
        add(result, "executor_active_count", Type.GAUGE, "count", "OPERATIONS", Set.of("lane"), "executor-saturation");
        add(result, "executor_queue_depth", Type.GAUGE, "count", "OPERATIONS", Set.of("lane"), "executor-saturation");
        add(result, "shadow_task_age_ms", Type.HISTOGRAM, "milliseconds", "OPERATIONS", Set.of("lane"), "task-age");
        add(result, "shadow_cancelled_count", Type.COUNTER, "count", "OPERATIONS", Set.of("lane","reason"), "cancellation");
        add(result, "checkpoint_lag_ms", Type.HISTOGRAM, "milliseconds", "DATA", Set.of("lane"), "checkpoint-lag");
        for (String metric : SAFETY_EXTENSION_METRICS) add(result, metric, Type.COUNTER, "count", "OPERATIONS", Set.of("lane"), "critical-zero");
        return Map.copyOf(result);
    }

    private static void add(Map<String, Descriptor> target, String name, Type type, String unit, String owner,
            Set<String> labels, String alert) {
        target.put(name, new Descriptor(type, unit, owner, labels, 64, "90d", alert,
                "NO_RAW_IDENTITY_TOKEN_FULL_ENDPOINT_OR_UNBOUNDED_ERROR"));
    }

    private static String required(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) throw new IllegalArgumentException("value required");
        return value;
    }
}
