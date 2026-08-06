package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SearchCtrActivationPolicyTest {

    private static final SearchCtrActivationPolicy.Window WINDOW =
            new SearchCtrActivationPolicy.Window(
                    Instant.parse("2026-08-06T00:00:00Z"),
                    Instant.parse("2026-08-06T01:00:00Z"));

    @Test
    void timingPolicyIsDerivedFromAttributionReplayAndClockSkewContracts() {
        assertEquals(Duration.ofHours(1), SearchCtrActivationPolicy.PROJECTION_WINDOW);
        assertEquals(Duration.ofMinutes(35), SearchCtrActivationPolicy.PROVISIONAL_GRACE);
        assertEquals(
                Duration.ofDays(30).plusMinutes(35),
                SearchCtrActivationPolicy.SETTLEMENT_GRACE);
    }

    @Test
    void provisionalEligibilityStartsAtTheInclusiveGraceBoundary() {
        Instant eligibleAt = Instant.parse("2026-08-06T01:35:00Z");

        assertEquals(eligibleAt, SearchCtrActivationPolicy.provisionalEligibleAt(WINDOW));
        assertFalse(SearchCtrActivationPolicy.isProvisionalEligible(
                WINDOW, eligibleAt.minusNanos(1)));
        assertTrue(SearchCtrActivationPolicy.isProvisionalEligible(WINDOW, eligibleAt));
    }

    @Test
    void settlementEligibilityStartsStrictlyAfterReplayClosure() {
        Instant threshold = Instant.parse("2026-09-05T01:35:00Z");

        assertEquals(threshold, SearchCtrActivationPolicy.settlementThreshold(WINDOW));
        assertFalse(SearchCtrActivationPolicy.isSettlementEligible(WINDOW, threshold));
        assertTrue(SearchCtrActivationPolicy.isSettlementEligible(
                WINDOW, threshold.plusNanos(1)));
    }

    @Test
    void currentStageKeepsRuntimeAndFinalityWritesDisabled() {
        assertEquals(
                SearchCtrActivationPolicy.RuntimeMode.DISABLED,
                SearchCtrActivationPolicy.AUTHORIZED_RUNTIME_MODE);
        assertFalse(SearchCtrActivationPolicy.isRuntimeWriteAuthorized());
        assertFalse(SearchCtrActivationPolicy.isFinalityWriteAuthorized());
    }

    @Test
    void windowsMustBeExactlyOneUtcAlignedHour() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchCtrActivationPolicy.Window(
                        Instant.parse("2026-08-06T00:15:00Z"),
                        Instant.parse("2026-08-06T01:15:00Z")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchCtrActivationPolicy.Window(
                        Instant.parse("2026-08-06T00:00:00Z"),
                        Instant.parse("2026-08-06T02:00:00Z")));
    }
}
