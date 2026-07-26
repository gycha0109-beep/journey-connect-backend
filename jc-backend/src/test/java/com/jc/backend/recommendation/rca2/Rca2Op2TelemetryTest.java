package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class Rca2Op2TelemetryTest {
    @Test void preservesTwentySevenAuthoritativeMetricsAndImplementsSevenBacklogMetrics() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new Rca2Op2Telemetry(registry);
        assertThat(Rca2Op2Telemetry.AUTHORITATIVE_METRICS).hasSize(27);
        assertThat(Rca2Op2Telemetry.BACKLOG_METRICS).containsExactly(
                "traffic_selected_count", "traffic_skipped_count", "executor_active_count",
                "executor_queue_depth", "shadow_task_age_ms", "shadow_cancelled_count", "checkpoint_lag_ms");
        assertThat(telemetry.definitions()).containsKeys(Rca2Op2Telemetry.AUTHORITATIVE_METRICS.toArray(String[]::new));
        assertThat(telemetry.definitions()).containsKeys(Rca2Op2Telemetry.BACKLOG_METRICS.toArray(String[]::new));

        telemetry.recordTrafficEvaluated(Rca2RuntimeContracts.Lane.P1, "evaluated");
        telemetry.recordTrafficSelected(Rca2RuntimeContracts.Lane.P1);
        telemetry.recordTrafficSkipped(Rca2RuntimeContracts.Lane.P2, "effective_traffic_zero");
        telemetry.recordMillis("shadow_task_age_milliseconds", Rca2RuntimeContracts.Lane.P1, "success", 12);
        telemetry.recordMillis("shadow_task_age_ms", Rca2RuntimeContracts.Lane.P1, "success", 12);
        telemetry.recordMillis("shadow_checkpoint_lag_seconds", Rca2RuntimeContracts.Lane.P1, "measured", 25);
        telemetry.recordMillis("checkpoint_lag_ms", Rca2RuntimeContracts.Lane.P1, "measured", 25);

        assertThat(telemetry.total("traffic_selected_count")).isEqualTo(1);
        assertThat(telemetry.total("traffic_skipped_count")).isEqualTo(1);
        assertThat(registry.find("rca2.op2.shadow_task_age_milliseconds").timer()).isNotNull();
        assertThat(registry.find("rca2.op2.checkpoint_lag_ms").timer()).isNotNull();
    }

    @Test void labelsAreBoundedAndRejectIdentityTokenUrlAndUnboundedErrorMaterial() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new Rca2Op2Telemetry(registry);
        telemetry.increment("shadow_exception_total", Rca2RuntimeContracts.Lane.P1,
                "https://user:token@example.test/path?identity=raw");
        var counter = registry.find("rca2.op2.shadow_exception_total").tag("class", "unknown").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTags()).allMatch(tag ->
                !tag.getValue().contains("token") && !tag.getValue().contains("http") && !tag.getValue().contains("@"));
        telemetry.definitions().values().forEach(descriptor -> {
            assertThat(descriptor.cardinalityLimit()).isLessThanOrEqualTo(64);
            assertThat(descriptor.redactionPolicy()).contains("NO_RAW_IDENTITY");
        });
    }

    @Test void runtimeGaugesExposeOnlyBoundedState() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new Rca2Op2Telemetry(registry);
        var killSwitch = new Rca2KillSwitch();
        try (var executor = new Rca2BoundedExecutor()) {
            telemetry.bindRuntime(executor, killSwitch);
            assertThat(registry.get("rca2.op2.executor_active_count").gauge().value()).isZero();
            assertThat(registry.get("rca2.op2.executor_queue_depth").gauge().value()).isZero();
            killSwitch.killGlobal();
            assertThat(registry.get("rca2.op2.global_kill_switch_state").gauge().value()).isEqualTo(1.0);
        }
    }
}
