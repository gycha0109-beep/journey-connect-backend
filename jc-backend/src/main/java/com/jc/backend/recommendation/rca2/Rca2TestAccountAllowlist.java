package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Default-deny synthetic/test-account allowlist keyed only by hashed subject reference. */
public final class Rca2TestAccountAllowlist {
    public static final String PURPOSE = "RCA2_ISOLATED_NONPRODUCTION_DARK_READ_ONLY";
    public static final String ENVIRONMENT = "ISOLATED_NON_PRODUCTION_RUNTIME";
    public static final Duration MAX_ENTRY_DURATION = Duration.ofDays(30);

    public enum Status {
        ALLOWED, NOT_FOUND, INVALID_HASH, EXPIRED, REVOKED, DISABLED,
        WRONG_PURPOSE, WRONG_ENVIRONMENT, DURATION_EXCEEDED, LOOKUP_FAILED
    }

    public record Entry(
            String hashedSubjectRef,
            String purpose,
            String environment,
            Instant validFrom,
            Instant validUntil,
            boolean revoked,
            boolean disabled,
            String auditReason) {
        public Entry {
            hashedSubjectRef = requireHash(hashedSubjectRef);
            purpose = require(purpose, "purpose");
            environment = require(environment, "environment");
            Objects.requireNonNull(validFrom, "validFrom");
            Objects.requireNonNull(validUntil, "validUntil");
            auditReason = require(auditReason, "auditReason");
            Duration duration = Duration.between(validFrom, validUntil);
            if (duration.isNegative() || duration.isZero() || duration.compareTo(MAX_ENTRY_DURATION) > 0) {
                throw new IllegalArgumentException("allowlist duration must be within 30 days");
            }
        }
    }

    public record Decision(boolean allowed, Status status, String hashedAuditRef) {
        static Decision denied(Status status, String hash) {
            return new Decision(false, status, hash == null ? "missing" : hash);
        }
    }

    public interface Provider {
        Optional<Entry> find(String hashedSubjectRef);
        boolean externalReady();
    }

    public static final class InMemoryNonProductionFixture implements Provider {
        private final Map<String, Entry> entries;

        public InMemoryNonProductionFixture(List<Entry> entries) {
            Map<String, Entry> copy = new HashMap<>();
            for (Entry entry : entries == null ? List.<Entry>of() : entries) {
                if (copy.putIfAbsent(entry.hashedSubjectRef(), entry) != null) {
                    throw new IllegalArgumentException("duplicate allowlist entry");
                }
            }
            this.entries = Map.copyOf(copy);
        }

        @Override public Optional<Entry> find(String hashedSubjectRef) {
            return Optional.ofNullable(entries.get(hashedSubjectRef));
        }

        @Override public boolean externalReady() { return false; }
    }

    public Decision evaluate(Provider provider, String hashedSubjectRef, String purpose, String environment, Instant now) {
        if (hashedSubjectRef == null || !hashedSubjectRef.matches("[0-9a-f]{64}")) {
            return Decision.denied(Status.INVALID_HASH, "missing");
        }
        if (provider == null) return Decision.denied(Status.LOOKUP_FAILED, hashedSubjectRef);
        final Optional<Entry> found;
        try {
            found = provider.find(hashedSubjectRef);
        } catch (RuntimeException exception) {
            return Decision.denied(Status.LOOKUP_FAILED, hashedSubjectRef);
        }
        if (found.isEmpty()) return Decision.denied(Status.NOT_FOUND, hashedSubjectRef);
        Entry entry = found.get();
        if (!PURPOSE.equals(purpose) || !entry.purpose().equals(purpose)) {
            return Decision.denied(Status.WRONG_PURPOSE, hashedSubjectRef);
        }
        if (!ENVIRONMENT.equals(environment) || !entry.environment().equals(environment)) {
            return Decision.denied(Status.WRONG_ENVIRONMENT, hashedSubjectRef);
        }
        if (entry.revoked()) return Decision.denied(Status.REVOKED, hashedSubjectRef);
        if (entry.disabled()) return Decision.denied(Status.DISABLED, hashedSubjectRef);
        if (Duration.between(entry.validFrom(), entry.validUntil()).compareTo(MAX_ENTRY_DURATION) > 0) {
            return Decision.denied(Status.DURATION_EXCEEDED, hashedSubjectRef);
        }
        if (now.isBefore(entry.validFrom()) || !now.isBefore(entry.validUntil())) {
            return Decision.denied(Status.EXPIRED, hashedSubjectRef);
        }
        return new Decision(true, Status.ALLOWED, hashedSubjectRef);
    }

    public static Provider unavailable() {
        return new Provider() {
            @Override public Optional<Entry> find(String hashedSubjectRef) { return Optional.empty(); }
            @Override public boolean externalReady() { return false; }
        };
    }

    private static String requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("hashedSubjectRef must be sha256");
        }
        return value;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
