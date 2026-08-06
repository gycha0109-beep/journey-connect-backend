package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SearchCtrManualActivationGateTest {

    @Test
    void currentPolicyAuthorizesOnlyTheBoundedSr6fgStageWindow() {
        SearchCtrManualActivationGate.ApprovedRun approved =
                SearchCtrManualActivationGate.current().approve(
                        authorizedProperties(),
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true);

        assertEquals("stage", approved.environment());
        assertEquals(Instant.parse("2026-08-06T08:00:00Z"), approved.window().start());
        assertEquals(Instant.parse("2026-08-06T09:00:00Z"), approved.window().end());
        assertEquals(
                SearchCtrActivationPolicy.AUTHORIZED_MANUAL_APPROVAL_REF,
                approved.approvalRef());
        assertTrue(approved.idempotencyKey().contains(SearchCtrActivationPolicy.POLICY_VERSION));
        assertTrue(approved.idempotencyKey().contains("sr6fg-stage-test-v1"));
    }

    @Test
    void environmentWindowApprovalAndBuildAreBoundToTheAuthorization() {
        SearchCtrManualActivationGate gate = SearchCtrManualActivationGate.current();

        SearchCtrManualActivationProperties wrongEnvironment = authorizedProperties();
        wrongEnvironment.setEnvironment("test");
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        wrongEnvironment,
                        new String[] {"test"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true));

        SearchCtrManualActivationProperties wrongWindow = authorizedProperties();
        wrongWindow.setWindowStart("2026-08-06T07:00:00Z");
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        wrongWindow,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true));

        SearchCtrManualActivationProperties wrongApproval = authorizedProperties();
        wrongApproval.setApprovalRef("approval:sr6fg-other-window");
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        wrongApproval,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true));

        SearchCtrManualActivationProperties wrongBuild = authorizedProperties();
        wrongBuild.setProducerBuildId("unapproved-build-v1");
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        wrongBuild,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true));
    }

    @Test
    void killSwitchProductionProfileAndMissingReliabilityCapabilityFailClosed() {
        SearchCtrManualActivationGate gate = SearchCtrManualActivationGate.current();
        SearchCtrManualActivationProperties properties = authorizedProperties();

        properties.setKillSwitch(true);
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true));

        properties.setKillSwitch(false);
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage", "production"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true));
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        properties,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        false));
    }

    @Test
    void provisionalThresholdAndUtcHourAlignmentRemainExact() {
        SearchCtrManualActivationGate gate = SearchCtrManualActivationGate.current();
        SearchCtrManualActivationProperties beforeThreshold = authorizedProperties();

        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        beforeThreshold,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T09:34:59Z"),
                        true));

        SearchCtrManualActivationProperties misaligned = authorizedProperties();
        misaligned.setWindowStart("2026-08-06T08:00:00.001Z");
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(
                        misaligned,
                        new String[] {"stage"},
                        Instant.parse("2026-08-06T10:00:00Z"),
                        true));
    }

    private static SearchCtrManualActivationProperties authorizedProperties() {
        SearchCtrManualActivationProperties properties = new SearchCtrManualActivationProperties();
        properties.setEnabled(true);
        properties.setKillSwitch(false);
        properties.setEnvironment(SearchCtrActivationPolicy.AUTHORIZED_MANUAL_ENVIRONMENT);
        properties.setWindowStart(
                SearchCtrActivationPolicy.AUTHORIZED_MANUAL_WINDOW_START.toString());
        properties.setProducerBuildId("sr6fg-stage-test-v1");
        properties.setApprovalRef(SearchCtrActivationPolicy.AUTHORIZED_MANUAL_APPROVAL_REF);
        return properties;
    }
}
