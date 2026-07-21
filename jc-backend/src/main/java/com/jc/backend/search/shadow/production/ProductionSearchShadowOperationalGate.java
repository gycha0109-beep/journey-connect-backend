package com.jc.backend.search.shadow.production;

import com.jc.intelligence.production.search.v1.ProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.ProductionShadowDispatchStatus;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskCompletionV1;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.SearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.SearchShadowMetricName;
import com.jc.intelligence.production.search.v1.SearchShadowMetricSink;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ProductionSearchShadowOperationalGate {
    private final ProductionSearchShadowRuntimeConfig config;
    private final SearchShadowKillSwitch killSwitch;
    private final ProductionShadowCohortSelector cohort;
    private final ProductionSearchShadowSamplingGate sampling;
    private final ProductionShadowTaskExecutor executor;
    private final SearchShadowMetricSink metrics;
    private final Clock clock;

    public ProductionSearchShadowOperationalGate(
            ProductionSearchShadowRuntimeConfig config,
            SearchShadowKillSwitch killSwitch,
            ProductionShadowCohortSelector cohort,
            ProductionSearchShadowSamplingGate sampling,
            ProductionShadowTaskExecutor executor,
            SearchShadowMetricSink metrics,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.killSwitch = Objects.requireNonNull(killSwitch, "killSwitch");
        this.cohort = Objects.requireNonNull(cohort, "cohort");
        this.sampling = Objects.requireNonNull(sampling, "sampling");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ProductionSearchShadowActivationDecision dispatch(
            Optional<String> accountHash,
            Runnable shadowTask,
            Consumer<ProductionShadowTaskCompletionV1> completion) {
        Objects.requireNonNull(accountHash, "accountHash");
        return dispatch(() -> accountHash, shadowTask, completion);
    }

    public ProductionSearchShadowActivationDecision dispatch(
            Supplier<Optional<String>> accountHashSupplier,
            Runnable shadowTask,
            Consumer<ProductionShadowTaskCompletionV1> completion) {
        Objects.requireNonNull(accountHashSupplier, "accountHashSupplier");
        Objects.requireNonNull(shadowTask, "shadowTask");
        Objects.requireNonNull(completion, "completion");

        if (!config.enabled()) {
            return blocked(ProductionSearchShadowActivationReason.DISABLED_BY_CONFIGURATION,
                    ProductionShadowDispatchStatus.KILLED, "configuration");
        }
        if (safeKilled()) {
            return blocked(ProductionSearchShadowActivationReason.DISABLED_BY_KILL_SWITCH,
                    ProductionShadowDispatchStatus.KILLED, "kill_switch");
        }
        if (config.effectiveSamplingBps() == 0) {
            return blocked(ProductionSearchShadowActivationReason.ZERO_SAMPLING,
                    ProductionShadowDispatchStatus.NOT_SAMPLED, "zero_sampling");
        }
        if (!config.operationalInputsPresent()) {
            return blocked(ProductionSearchShadowActivationReason.OPERATIONAL_INPUTS_MISSING,
                    ProductionShadowDispatchStatus.KILLED, "operational_inputs_missing");
        }
        if (!config.activationWindowAllows(clock.instant())) {
            return blocked(ProductionSearchShadowActivationReason.ACTIVATION_WINDOW_CLOSED,
                    ProductionShadowDispatchStatus.KILLED, "activation_window_closed");
        }
        if (!config.hasCohort()) {
            return blocked(ProductionSearchShadowActivationReason.EMPTY_ALLOWLIST,
                    ProductionShadowDispatchStatus.COHORT_REJECTED, "empty_allowlist");
        }
        Optional<String> accountHash;
        try {
            accountHash = Objects.requireNonNull(accountHashSupplier.get(), "accountHash");
        } catch (RuntimeException ignored) {
            accountHash = Optional.empty();
        }
        if (accountHash.isEmpty()) {
            return blocked(ProductionSearchShadowActivationReason.ANONYMOUS_SUBJECT,
                    ProductionShadowDispatchStatus.COHORT_REJECTED, "anonymous_subject");
        }
        String key = accountHash.orElseThrow();
        if (!safeCohortIncludes(key)) {
            return blocked(ProductionSearchShadowActivationReason.NOT_IN_COHORT,
                    ProductionShadowDispatchStatus.COHORT_REJECTED, "not_in_cohort");
        }
        safeIncrement(SearchShadowMetricName.COHORT_ACCEPTED, Map.of("outcome", "accepted"));
        var samplingDecision = sampling.decide(key);
        if (!samplingDecision.included()) {
            safeIncrement(SearchShadowMetricName.SKIPPED, Map.of("reason", "sampling"));
            return new ProductionSearchShadowActivationDecision(
                    ProductionSearchShadowActivationReason.NOT_SAMPLED,
                    ProductionShadowDispatchStatus.NOT_SAMPLED,
                    samplingDecision);
        }
        safeIncrement(SearchShadowMetricName.SAMPLED, Map.of("outcome", "accepted"));
        ProductionShadowDispatchStatus status;
        try {
            status = executor.submitTimed(
                    shadowTask,
                    config.runtimeTimeout(),
                    config.hardTimeout(),
                    completion);
        } catch (RuntimeException ignored) {
            status = ProductionShadowDispatchStatus.REJECTED;
        }
        if (status == ProductionShadowDispatchStatus.SUBMITTED) {
            safeIncrement(SearchShadowMetricName.DISPATCHED, Map.of("outcome", "accepted"));
            safeGauge();
            return new ProductionSearchShadowActivationDecision(
                    ProductionSearchShadowActivationReason.DISPATCHED,
                    status,
                    samplingDecision);
        }
        safeIncrement(SearchShadowMetricName.REJECTED, Map.of("reason", boundedReason(status)));
        return new ProductionSearchShadowActivationDecision(
                ProductionSearchShadowActivationReason.RESOURCE_REJECTED,
                status,
                samplingDecision);
    }

    private ProductionSearchShadowActivationDecision blocked(
            ProductionSearchShadowActivationReason reason,
            ProductionShadowDispatchStatus status,
            String metricReason) {
        SearchShadowMetricName metric = reason == ProductionSearchShadowActivationReason.DISABLED_BY_KILL_SWITCH
                ? SearchShadowMetricName.KILLED : SearchShadowMetricName.SKIPPED;
        safeIncrement(metric, Map.of("reason", metricReason));
        return new ProductionSearchShadowActivationDecision(reason, status, null);
    }

    private boolean safeKilled() {
        try {
            return killSwitch.killed();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private boolean safeCohortIncludes(String key) {
        try {
            return cohort.includes(key);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void safeGauge() {
        try {
            metrics.recordGauge(SearchShadowMetricName.QUEUE_DEPTH, executor.queueDepth(), Map.of("environment", "prod"));
            metrics.recordGauge(SearchShadowMetricName.EXECUTOR_ACTIVE, executor.activeCount(), Map.of("environment", "prod"));
        } catch (RuntimeException ignored) {
            // Observability is isolated from legacy response handling.
        }
    }

    private void safeIncrement(SearchShadowMetricName name, Map<String, String> tags) {
        try {
            metrics.increment(name, tags);
        } catch (RuntimeException ignored) {
            // Observability is isolated from legacy response handling.
        }
    }

    private static String boundedReason(ProductionShadowDispatchStatus status) {
        return switch (status) {
            case QUEUE_FULL -> "queue_full";
            case EXECUTOR_UNAVAILABLE -> "executor_unavailable";
            default -> "rejected";
        };
    }
}
