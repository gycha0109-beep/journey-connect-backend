package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Rca2Op2AlertPolicyTest {
    @Test void criticalZeroToleranceSignalFailsClosedWhenRouteIsUnavailable() {
        var policy = new Rca2Op2AlertPolicy();
        var killSwitch = new Rca2KillSwitch();
        var alerts = policy.evaluate(new Rca2Op2AlertPolicy.Snapshot(100,
                        Map.of("shadow_response_mutation_total", 1L), Set.of()),
                Rca2RuntimeContracts.Lane.P1, "window_001", Rca2Op2AlertPolicy.unavailableRoute(), killSwitch);
        assertThat(alerts).extracting(Rca2Op2AlertPolicy.Alert::id).contains("response_mutation_detected");
        assertThat(alerts.get(0).deliveryStatus()).isEqualTo(Rca2Op2AlertPolicy.DeliveryStatus.ROUTE_UNAVAILABLE);
        assertThat(killSwitch.globalKilled()).isTrue();
    }

    @Test void sc6WarningThresholdBoundariesAreUnchangedAndAlertsDeduplicate() {
        var policy = new Rca2Op2AlertPolicy();
        var killSwitch = new Rca2KillSwitch();
        var snapshot = new Rca2Op2AlertPolicy.Snapshot(100, Map.of(
                "shadow_timeout_total", 20L,
                "shadow_exception_total", 25L,
                "shadow_queue_rejection_total", 5L,
                "shadow_late_discard_total", 5L), Set.of());
        var first = policy.evaluate(snapshot, Rca2RuntimeContracts.Lane.P2, "window_002",
                ignored -> Rca2Op2AlertPolicy.DeliveryStatus.DELIVERED, killSwitch);
        assertThat(first).extracting(Rca2Op2AlertPolicy.Alert::id).contains(
                "timeout_rate", "exception_rate", "queue_rejection_rate", "late_discard_rate");
        assertThat(policy.evaluate(snapshot, Rca2RuntimeContracts.Lane.P2, "window_002",
                ignored -> Rca2Op2AlertPolicy.DeliveryStatus.DELIVERED, killSwitch)).isEmpty();
        assertThat(killSwitch.globalKilled()).isFalse();
    }

    @Test void alertPayloadKeyIsBounded() {
        var policy = new Rca2Op2AlertPolicy();
        assertThatThrownBy(() -> policy.evaluate(new Rca2Op2AlertPolicy.Snapshot(1, Map.of(), Set.of()),
                Rca2RuntimeContracts.Lane.P1, "https://raw-endpoint/token", Rca2Op2AlertPolicy.unavailableRoute(),
                new Rca2KillSwitch())).isInstanceOf(IllegalArgumentException.class);
    }
}
