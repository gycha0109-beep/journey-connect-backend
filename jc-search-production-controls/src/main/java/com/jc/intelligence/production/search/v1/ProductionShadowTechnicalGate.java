package com.jc.intelligence.production.search.v1;

import java.util.Map;
import java.util.Objects;

/**
 * Fail-closed technical gate. Internal control/metric failures are isolated from the legacy caller.
 * This gate provides capability only and does not grant production activation authority.
 */
public final class ProductionShadowTechnicalGate<T> {
    private final SearchShadowKillSwitch killSwitch;
    private final ProductionShadowCohortSelector cohort;
    private final ProductionShadowSamplingAuthorization sampling;
    private final ProductionShadowTaskExecutor executor;
    private final SearchShadowMetricSink metrics;

    public ProductionShadowTechnicalGate(SearchShadowKillSwitch killSwitch,
            ProductionShadowCohortSelector cohort,
            ProductionShadowSamplingAuthorization sampling,
            ProductionShadowTaskExecutor executor,
            SearchShadowMetricSink metrics) {
        this.killSwitch = Objects.requireNonNull(killSwitch, "killSwitch");
        this.cohort = Objects.requireNonNull(cohort, "cohort");
        this.sampling = Objects.requireNonNull(sampling, "sampling");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public ProductionShadowDispatchReceiptV1<T> dispatch(ProductionShadowDispatchRequestV1<T> request) {
        Objects.requireNonNull(request, "request");
        int effective = safeEffectiveBasisPoints();
        if (safeKilled()) {
            safeIncrement(SearchShadowMetricName.KILLED, Map.of("reason", "kill_switch"));
            return receipt(request, ProductionShadowDispatchStatus.KILLED, effective, "kill_switch_killed");
        }
        safeIncrement(SearchShadowMetricName.ELIGIBLE, Map.of("stage", "technical"));
        if (!safeCohortIncludes(request.opaqueInternalCohortKey())) {
            safeIncrement(SearchShadowMetricName.SKIPPED, Map.of("reason", "cohort"));
            return receipt(request, ProductionShadowDispatchStatus.COHORT_REJECTED, effective, "cohort_rejected");
        }
        safeIncrement(SearchShadowMetricName.COHORT_ACCEPTED, Map.of("stage", "internal"));
        if (!safeSampleIncluded(request.stableSamplingKey())) {
            safeIncrement(SearchShadowMetricName.SKIPPED, Map.of("reason", "sample"));
            return receipt(request, ProductionShadowDispatchStatus.NOT_SAMPLED, effective, "not_sampled");
        }
        safeIncrement(SearchShadowMetricName.SAMPLED, Map.of("stage", "technical"));
        ProductionShadowDispatchStatus status = safeSubmit(request.shadowTask());
        if (status == ProductionShadowDispatchStatus.SUBMITTED) {
            safeIncrement(SearchShadowMetricName.DISPATCHED, Map.of("stage", "technical"));
        } else {
            safeIncrement(SearchShadowMetricName.REJECTED, Map.of("reason", status.wireValue()));
        }
        return receipt(request, status, effective,
                status == ProductionShadowDispatchStatus.SUBMITTED ? "submitted" : "dispatch_rejected");
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

    private int safeEffectiveBasisPoints() {
        try {
            int value = sampling.effectiveBasisPoints();
            return value >= 0 && value <= 10_000 ? value : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private boolean safeSampleIncluded(String key) {
        try {
            return sampling.decide(key).included();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private ProductionShadowDispatchStatus safeSubmit(Runnable task) {
        try {
            return executor.submit(task);
        } catch (RuntimeException ignored) {
            return ProductionShadowDispatchStatus.REJECTED;
        }
    }

    private void safeIncrement(SearchShadowMetricName name, Map<String, String> tags) {
        try {
            metrics.increment(name, tags);
        } catch (RuntimeException ignored) {
            // Metrics are observational and never acquire legacy response authority.
        }
    }

    private static <T> ProductionShadowDispatchReceiptV1<T> receipt(
            ProductionShadowDispatchRequestV1<T> request,
            ProductionShadowDispatchStatus status,
            int effective,
            String reason) {
        return new ProductionShadowDispatchReceiptV1<>(request.legacyResponse(), status, effective, reason);
    }
}
