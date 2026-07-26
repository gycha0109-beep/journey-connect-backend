package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Short-lived non-production workload identity boundary. Raw token material is never printable. */
public interface Rca2WorkloadCredentialProvider {
    Duration MAX_TTL = Duration.ofSeconds(3_600);
    Duration REFRESH_MARGIN = Duration.ofSeconds(60);
    String REQUIRED_AUDIENCE = "rca2-isolated-nonproduction-candidate";
    String TYPE = "SHORT_LIVED_NONPRODUCTION_WORKLOAD_IDENTITY";

    Optional<Lease> current(Instant now);
    RefreshResult refresh(Instant now);
    void revoke(String credentialIdHash, Instant now);
    boolean externalReady();

    enum Status {
        READY, MISSING, EXPIRED, STALE, REVOKED, TTL_EXCEEDED, WRONG_AUDIENCE,
        PRODUCTION_SCOPE, WRITE_SCOPE, REFRESH_FAILED, EXTERNAL_PROVIDER_UNRESOLVED
    }

    record RefreshResult(Status status, String credentialIdHash) {
        public RefreshResult {
            Objects.requireNonNull(status, "status");
            credentialIdHash = redactHash(credentialIdHash);
        }
    }

    final class Lease implements AutoCloseable {
        private final char[] token;
        private final String credentialIdHash;
        private final String audience;
        private final Set<String> scopes;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private final boolean revoked;

        public Lease(char[] token, String credentialIdHash, String audience, Set<String> scopes,
                Instant issuedAt, Instant expiresAt, boolean revoked) {
            this.token = token == null ? new char[0] : Arrays.copyOf(token, token.length);
            this.credentialIdHash = requireHash(credentialIdHash);
            this.audience = require(audience, "audience");
            this.scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
            this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            this.revoked = revoked;
        }

        public String credentialIdHash() { return credentialIdHash; }
        public String audience() { return audience; }
        public Set<String> scopes() { return scopes; }
        public Instant issuedAt() { return issuedAt; }
        public Instant expiresAt() { return expiresAt; }
        public boolean revoked() { return revoked; }
        public char[] copyToken() { return Arrays.copyOf(token, token.length); }
        public boolean refreshRequired(Instant now) { return !now.plus(REFRESH_MARGIN).isBefore(expiresAt); }

        @Override public void close() { Arrays.fill(token, '\0'); }
        @Override public String toString() { return "Lease[credentialIdHash=" + credentialIdHash + ",token=REDACTED]"; }
    }

    static Status validate(Lease lease, Instant now) {
        if (lease == null) return Status.MISSING;
        if (lease.revoked()) return Status.REVOKED;
        if (!now.isBefore(lease.expiresAt())) return Status.EXPIRED;
        Duration ttl = Duration.between(lease.issuedAt(), lease.expiresAt());
        if (ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) return Status.TTL_EXCEEDED;
        if (!REQUIRED_AUDIENCE.equals(lease.audience())) return Status.WRONG_AUDIENCE;
        if (lease.scopes().stream().anyMatch(Rca2WorkloadCredentialProvider::productionScope)) {
            return Status.PRODUCTION_SCOPE;
        }
        if (lease.scopes().stream().anyMatch(Rca2WorkloadCredentialProvider::writeScope)) {
            return Status.WRITE_SCOPE;
        }
        if (lease.copyToken().length == 0) return Status.MISSING;
        return lease.refreshRequired(now) ? Status.STALE : Status.READY;
    }

    static Rca2WorkloadCredentialProvider unavailable() {
        return new Rca2WorkloadCredentialProvider() {
            @Override public Optional<Lease> current(Instant now) { return Optional.empty(); }
            @Override public RefreshResult refresh(Instant now) {
                return new RefreshResult(Status.EXTERNAL_PROVIDER_UNRESOLVED, "missing");
            }
            @Override public void revoke(String credentialIdHash, Instant now) { }
            @Override public boolean externalReady() { return false; }
        };
    }

    static String requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("credential identifier must be sha256");
        }
        return value;
    }

    static String redactHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}") ? value : "missing";
    }

    private static boolean productionScope(String scope) {
        String value = scope == null ? "" : scope.toLowerCase(java.util.Locale.ROOT);
        return value.contains("prod") || value.contains("production") || value.contains("live");
    }

    private static boolean writeScope(String scope) {
        String value = scope == null ? "" : scope.toLowerCase(java.util.Locale.ROOT);
        return value.contains("write") || value.contains("mutate") || value.contains("admin") || value.contains("owner");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
