package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class Rca2Op2MetricsTest {
    private static final Rca2RuntimeContracts.Lane LANE = Rca2RuntimeContracts.Lane.P1;
    private static final Rca2RuntimeContracts.BreakerState BREAKER = Rca2RuntimeContracts.BreakerState.CLOSED;

    @Test
    void exposesAuthoritativeAndBacklogMetricInventory() {
        Rca2Metrics metrics = Rca2Metrics.inMemory();
        assertThat(Rca2Metrics.REQUIRED).hasSize(27);
        assertThat(Rca2Metrics.OP2_BACKLOG).containsExactly(
                "traffic_selected_count", "traffic_skipped_count", "executor_active_count", "executor_queue_depth",
                "shadow_task_age_ms", "shadow_cancelled_count", "checkpoint_lag_ms");
        assertThat(metrics.definitions()).containsEntry("executor_active_count", "GAUGE")
                .containsEntry("shadow_task_age_ms", "HISTOGRAM")
                .containsEntry("traffic_selected_count", "COUNTER");
    }

    @Test
    void recordsCounterGaugeAndTimerWithoutUnboundedLabels() {
        Rca2Metrics metrics = Rca2Metrics.inMemory();
        metrics.increment("traffic_selected_count", LANE, "selected", BREAKER);
        metrics.setGauge("executor_active_count", LANE, "active", BREAKER, 2);
        metrics.recordMillis("shadow_task_age_ms", LANE, "accepted", BREAKER, 25);
        assertThat(metrics.total("traffic_selected_count")).isEqualTo(1);
        assertThat(metrics.total("executor_active_count")).isEqualTo(2);
        assertThat(metrics.total("shadow_task_age_ms")).isEqualTo(25);
        assertThat(metrics.sampleCount("traffic_selected_count")).isEqualTo(1);
        assertThat(metrics.sampleCount("executor_active_count")).isEqualTo(1);
        assertThat(metrics.sampleCount("shadow_task_age_ms")).isEqualTo(1);
        assertThat(Rca2Metrics.ALLOWED_LABELS).containsExactlyInAnyOrder(
                "environment", "lane", "result_class", "breaker_state");
    }

    @Test
    void registersMicrometerCounterGaugeAndTimerWithBoundedTags() {
        var registry = new SimpleMeterRegistry();
        Rca2Metrics metrics = new Rca2Metrics(registry);
        metrics.increment("traffic_selected_count", LANE, "selected", BREAKER);
        metrics.setGauge("executor_active_count", LANE, "executor", BREAKER, 2);
        metrics.setGauge("executor_active_count", LANE, "executor", BREAKER, 1);
        metrics.recordMillis("shadow_task_age_ms", LANE, "success", BREAKER, 25);

        assertThat(registry.get("rca2.traffic_selected_count").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("rca2.executor_active_count").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("rca2.shadow_task_age_ms").timer().count()).isEqualTo(1L);
        assertThat(registry.get("rca2.shadow_task_age_ms").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(25.0);
        registry.close();
    }

    @Test
    void sanitizesPotentialIdentityTokenUrlAndErrorLabels() {
        Rca2Metrics metrics = Rca2Metrics.inMemory();
        metrics.increment("traffic_skipped_count", LANE, "https://candidate.example/path?token=secret", BREAKER);
        metrics.increment("traffic_skipped_count", LANE, "user:12345", BREAKER);
        metrics.increment("traffic_skipped_count", LANE, "Bearer secret", BREAKER);
        assertThat(metrics.total("traffic_skipped_count")).isEqualTo(3);
    }

    @Test
    void rejectsNegativeGaugeAndMetricTypeConfusion() {
        Rca2Metrics metrics = Rca2Metrics.inMemory();
        assertThatThrownBy(() -> metrics.setGauge("executor_queue_depth", LANE, "queued", BREAKER, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.increment("shadow_task_age_ms", LANE, "age", BREAKER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
