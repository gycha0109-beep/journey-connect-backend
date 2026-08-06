package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SearchCtrManualActivationGateTest {

    @Test
    void currentPolicyKeepsManualActivationUnauthorized() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> SearchCtrManualActivationGate.current().approve(
                        enabledProperties(),
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T02:00:00Z"),
                        true));

        assertTrue(error.getMessage().contains("runtime mode is not authorized"));
    }

    @Test
    void approvedCandidateRequiresAllowlistedProfileKillSwitchOffAndReliabilityCapability() {
        SearchCtrManualActivationGate gate = new SearchCtrManualActivationGate(
                SearchCtrActivationPolicy.RuntimeMode.NONPRODUCTION_MANUAL);
        SearchCtrManualActivationProperties properties = enabledProperties();

        SearchCtrManualActivationGate.ApprovedRun approved = gate.approve(
                properties,
                new String[] {"stage"},
                Instant.parse("2026-08-06T02:00:00Z"),
                true);

        assertEquals("stage", approved.environment());
        assertEquals(Instant.parse("2026-08-06T00:00:00Z"), approved.window().start());
        assertEquals(Instant.parse("2026-08-06T01:00:00Z"), approved.window().end());
        assertTrue(approved.idempotencyKey().contains(SearchCtrActivationPolicy.POLICY_VERSION));
        assertTrue(approved.idempotencyKey().contains("sr6ff-test-v1"));
    }

    @Test
    void killSwitchProductionProfileAndMissingReliabilityCapabilityFailClosed() {
        SearchCtrManualActivationGate gate = new SearchCtrManualActivationGate(
                SearchCtrActivationPolicy.RuntimeMode.NONPRODUCTION_MANUAL);
        SearchCtrManualActivationProperties properties = enabledProperties();

        properties.setKillSwitch(true);
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T02:00:00Z"),
                        true));

        properties.setKillSwitch(false);
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage", "production"},
                        Instant.parse("2026-08-06T02:00:00Z"),
                        true));
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T02:00:00Z"),
                        false));
    }

    @Test
    void provisionalThresholdAndUtcHourAlignmentRemainExact() {
        SearchCtrManualActivationGate gate = new SearchCtrManualActivationGate(
                SearchCtrActivationPolicy.RuntimeMode.NONPRODUCTION_MANUAL);
        SearchCtrManualActivationProperties properties = enabledProperties();

        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T01:34:59Z"),
                        true));

        properties.setWindowStart("2026-08-06T00:00:00.001Z");
        assertThrows(
                IllegalArgumentException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T02:00:00Z"),
                        true));
    }

    private static SearchCtrManualActivationProperties enabledProperties() {
        SearchCtrManualActivationProperties properties = new SearchCtrManualActivationProperties();
        properties.setEnabled(true);
        properties.setKillSwitch(false);
        properties.setEnvironment("stage");
        properties.setWindowStart("2026-08-06T00:00:00Z");
        properties.setProducerBuildId("sr6ff-test-v1");
        properties.setApprovalRef("approval:sr6ff-test-v1");
        return properties;
    }
}
