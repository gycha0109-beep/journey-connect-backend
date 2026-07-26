package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Rca2CredentialNetworkContract {
    public static final String CREDENTIAL_OWNER = "OPERATIONS";
    public static final String CREDENTIAL_STORAGE = "PLATFORM_SECRET_MANAGER";
    public static final Duration CREDENTIAL_MAX_TTL = Duration.ofSeconds(3_600);
    public static final String NETWORK_POLICY = "DENY_BY_DEFAULT_EXPLICIT_NONPRODUCTION_ALLOWLIST";

    public record CredentialMetadata(
            String credentialRefHash,
            String environment,
            Instant issuedAt,
            Instant expiresAt,
            Set<String> scopes,
            boolean ownerOrSuperuser,
            boolean writeCapable,
            String owner,
            String storage) {
        public CredentialMetadata {
            credentialRefHash = requireHash(credentialRefHash);
            environment = require(environment, "environment");
            Objects.requireNonNull(issuedAt, "issuedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
            scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
            owner = require(owner, "owner");
            storage = require(storage, "storage");
        }
    }

    public record EndpointContract(
            String endpointAliasHash,
            String environment,
            boolean tls,
            boolean explicitlyAllowlisted,
            boolean productionRoute) {
        public EndpointContract {
            endpointAliasHash = requireHash(endpointAliasHash);
            environment = require(environment, "environment");
        }
    }

    public enum Rejection {
        NONE, MISSING, EXPIRED, TTL_EXCEEDED, WRONG_ENVIRONMENT, OWNER_SCOPE,
        WRITE_SCOPE, OWNER_MISMATCH, STORAGE_MISMATCH, TLS_REQUIRED,
        NOT_ALLOWLISTED, PRODUCTION_ROUTE
    }

    public record Decision(boolean allowed, Rejection rejection, String redactedReference) {}

    public interface Provider {
        Optional<CredentialMetadata> current();
        void revoke();
    }

    public static final class NonProductionFakeProvider implements Provider {
        private Optional<CredentialMetadata> current;
        public NonProductionFakeProvider(CredentialMetadata metadata) { current = Optional.ofNullable(metadata); }
        @Override public Optional<CredentialMetadata> current() { return current; }
        @Override public void revoke() { current = Optional.empty(); }
    }

    public Decision validateCredential(Provider provider, Instant now) {
        if (provider == null) return denied(Rejection.MISSING, "missing");
        Optional<CredentialMetadata> optional = provider.current();
        if (optional.isEmpty()) return denied(Rejection.MISSING, "missing");
        CredentialMetadata value = optional.get();
        String ref = value.credentialRefHash();
        if (!Rca2RuntimeContracts.ENVIRONMENT.equals(value.environment())) return denied(Rejection.WRONG_ENVIRONMENT, ref);
        if (!now.isBefore(value.expiresAt())) return denied(Rejection.EXPIRED, ref);
        Duration ttl = Duration.between(value.issuedAt(), value.expiresAt());
        if (ttl.isNegative() || ttl.compareTo(CREDENTIAL_MAX_TTL) > 0) return denied(Rejection.TTL_EXCEEDED, ref);
        if (value.ownerOrSuperuser()) return denied(Rejection.OWNER_SCOPE, ref);
        if (value.writeCapable() || value.scopes().stream().anyMatch(scope -> scope.contains("write") || scope.contains("owner"))) {
            return denied(Rejection.WRITE_SCOPE, ref);
        }
        if (!CREDENTIAL_OWNER.equals(value.owner())) return denied(Rejection.OWNER_MISMATCH, ref);
        if (!CREDENTIAL_STORAGE.equals(value.storage())) return denied(Rejection.STORAGE_MISMATCH, ref);
        return new Decision(true, Rejection.NONE, ref);
    }

    public Decision validateEndpoint(EndpointContract endpoint) {
        if (endpoint == null) return denied(Rejection.NOT_ALLOWLISTED, "missing");
        String ref = endpoint.endpointAliasHash();
        if (!Rca2RuntimeContracts.ENVIRONMENT.equals(endpoint.environment())) return denied(Rejection.WRONG_ENVIRONMENT, ref);
        if (endpoint.productionRoute()) return denied(Rejection.PRODUCTION_ROUTE, ref);
        if (!endpoint.tls()) return denied(Rejection.TLS_REQUIRED, ref);
        if (!endpoint.explicitlyAllowlisted()) return denied(Rejection.NOT_ALLOWLISTED, ref);
        return new Decision(true, Rejection.NONE, ref);
    }

    private static Decision denied(Rejection rejection, String ref) { return new Decision(false, rejection, ref); }
    private static String requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("redacted reference must be sha256");
        return value;
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
