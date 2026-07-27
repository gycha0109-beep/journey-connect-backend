package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(Rca2Metrics.ALLOWED_LABELS).containsExactlyInAnyOrder(
                "environment", "lane", "result_class", "breaker_state");
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
