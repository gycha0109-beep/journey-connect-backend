package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class Rca2FeatureFlagPolicy {
    public static final Duration REFRESH_INTERVAL = Duration.ofSeconds(30);
    public static final Duration STALE_AFTER = Duration.ofSeconds(120);
    public static final Duration MAX_TTL = Duration.ofDays(30);

    public record Snapshot(
            String globalValue,
            boolean p1Enabled,
            boolean p2Enabled,
            int trafficPercent,
            String environment,
            String version,
            Instant loadedAt,
            Instant refreshedAt,
            Instant expiresAt,
            boolean signatureVerified) {
        public Snapshot {
            globalValue = globalValue == null ? "missing" : globalValue;
            environment = environment == null ? "missing" : environment;
            version = version == null ? "missing" : version;
            Objects.requireNonNull(loadedAt, "loadedAt");
            Objects.requireNonNull(refreshedAt, "refreshedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
        public static Snapshot defaultOff(Instant now) {
            return new Snapshot("off", false, false, 0, Rca2RuntimeContracts.ENVIRONMENT,
                    "rca2-flag-default-off-v1", now, now, now.plus(MAX_TTL), true);
        }
    }

    public enum Reason {
        ENABLED, MISSING, UNKNOWN, MALFORMED, EXPIRED, STALE, INVALID_ENVIRONMENT,
        INVALID_LANE, INVALID_TRAFFIC, UNVERIFIED, FLAG_OFF, TRAFFIC_ZERO
    }

    public record Decision(boolean enabled, Reason reason, int trafficPercent, String version) {}

    private final AtomicReference<Snapshot> current;

    public Rca2FeatureFlagPolicy(Snapshot initial) {
        current = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    public void replace(Snapshot snapshot) { current.set(Objects.requireNonNull(snapshot, "snapshot")); }
    public Snapshot snapshot() { return current.get(); }

    public Decision evaluate(Rca2RuntimeContracts.Lane lane, Instant now) {
        Snapshot value = current.get();
        if (lane == null) return off(Reason.INVALID_LANE, value);
        if (!value.signatureVerified()) return off(Reason.UNVERIFIED, value);
        if (!Rca2RuntimeContracts.ENVIRONMENT.equals(value.environment())) return off(Reason.INVALID_ENVIRONMENT, value);
        if (value.trafficPercent() < 0 || value.trafficPercent() > 100) return off(Reason.INVALID_TRAFFIC, value);
        if (value.expiresAt().isBefore(value.loadedAt()) || value.expiresAt().isAfter(value.loadedAt().plus(MAX_TTL))) {
            return off(Reason.MALFORMED, value);
        }
        if (!now.isBefore(value.expiresAt())) return off(Reason.EXPIRED, value);
        if (Duration.between(value.refreshedAt(), now).isNegative()
                || Duration.between(value.refreshedAt(), now).compareTo(STALE_AFTER) > 0) {
            return off(Reason.STALE, value);
        }
        String normalized = value.globalValue().trim().toLowerCase(Locale.ROOT);
        if (!value.globalValue().equals(value.globalValue().trim())) return off(Reason.MALFORMED, value);
        if (!normalized.equals("on") && !normalized.equals("off")) {
            return off(value.globalValue().equals("missing") ? Reason.MISSING : Reason.UNKNOWN, value);
        }
        if (normalized.equals("off")) return off(Reason.FLAG_OFF, value);
        if ((lane == Rca2RuntimeContracts.Lane.P1 && !value.p1Enabled())
                || (lane == Rca2RuntimeContracts.Lane.P2 && !value.p2Enabled())) {
            return off(Reason.INVALID_LANE, value);
        }
        if (value.trafficPercent() == 0) return off(Reason.TRAFFIC_ZERO, value);
        return new Decision(true, Reason.ENABLED, value.trafficPercent(), value.version());
    }

    private static Decision off(Reason reason, Snapshot value) {
        return new Decision(false, reason, 0, value.version());
    }
}
