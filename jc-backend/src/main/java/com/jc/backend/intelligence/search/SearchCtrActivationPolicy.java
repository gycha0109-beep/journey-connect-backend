package com.jc.backend.intelligence.search;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SearchCtrActivationPolicy {

    public static final String POLICY_VERSION = "search-ctr-activation-finality-v1";
    public static final Duration PROJECTION_WINDOW = Duration.ofHours(1);
    public static final Duration PROVISIONAL_GRACE =
            SearchCtrContract.ATTRIBUTION_WINDOW.plus(SearchBehaviorContract.MAX_FUTURE_SKEW);
    public static final Duration SETTLEMENT_GRACE =
            SearchBehaviorContract.MAX_EVENT_AGE.plus(PROVISIONAL_GRACE);
    public static final RuntimeMode AUTHORIZED_RUNTIME_MODE = RuntimeMode.DISABLED;

    private SearchCtrActivationPolicy() {}

    public static Instant provisionalEligibleAt(Window window) {
        return validated(window).end().plus(PROVISIONAL_GRACE);
    }

    public static Instant settlementThreshold(Window window) {
        return validated(window).end().plus(SETTLEMENT_GRACE);
    }

    public static boolean isProvisionalEligible(Window window, Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        return !observedAt.isBefore(provisionalEligibleAt(window));
    }

    public static boolean isSettlementEligible(Window window, Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        return observedAt.isAfter(settlementThreshold(window));
    }

    public static boolean isRuntimeWriteAuthorized() {
        return AUTHORIZED_RUNTIME_MODE != RuntimeMode.DISABLED;
    }

    public static boolean isFinalityWriteAuthorized() {
        return false;
    }

    private static Window validated(Window window) {
        return Objects.requireNonNull(window, "window");
    }

    public enum RuntimeMode {
        DISABLED,
        NONPRODUCTION_MANUAL,
        NONPRODUCTION_SCHEDULED,
        PRODUCTION_SCHEDULED
    }

    public record Window(Instant start, Instant end) {
        public Window {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!end.equals(start.plus(PROJECTION_WINDOW))) {
                throw new IllegalArgumentException("Search CTR window must be exactly one hour");
            }
            if (start.getNano() != 0
                    || Math.floorMod(start.getEpochSecond(), PROJECTION_WINDOW.toSeconds()) != 0) {
                throw new IllegalArgumentException("Search CTR window must align to a UTC hour");
            }
        }
    }
}
