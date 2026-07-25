package com.jc.backend.recommendation.rca2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

public final class Rca2IdentityPolicy {
    public static final String AUTHORITY = "RCA2_NONPRODUCTION_TEST_ACCOUNT_ALLOWLIST_V1";
    public static final String PURPOSE = "RCA2_ISOLATED_NONPRODUCTION_DARK_READ_ONLY";
    public static final int ALLOWLIST_ENTRY_MAX_DAYS = 30;
    public static final int HASHED_AUDIT_RETENTION_DAYS = 90;

    public record Identity(
            String ref,
            String purpose,
            String caller,
            String environment,
            Instant validUntil,
            boolean deleted,
            boolean invalidated,
            boolean encryptedAtRest,
            boolean encryptedInTransit) {}

    public enum Reason {
        ALLOWED_SYNTHETIC, ALLOWED_TEST_ACCOUNT, ABSENT, INVALID, EXPIRED, DELETED,
        MISMATCHED, UNAUTHORIZED_PURPOSE, UNAUTHORIZED_CALLER, WRONG_ENVIRONMENT,
        ACTUAL_PRODUCTION_IDENTITY_BLOCKED, ENCRYPTION_CONTRACT_FAILED
    }

    public record Decision(boolean allowed, Reason reason, String hashedAuditRef) {}

    private final Set<String> allowedTestAccountHashes;
    private final Set<String> allowedCallers;

    public Rca2IdentityPolicy(Set<String> allowedTestAccountHashes, Set<String> allowedCallers) {
        this.allowedTestAccountHashes = Set.copyOf(allowedTestAccountHashes == null ? Set.of() : allowedTestAccountHashes);
        this.allowedCallers = Set.copyOf(allowedCallers == null ? Set.of("rca2-post-response-hook") : allowedCallers);
    }

    public Decision validate(Identity identity, Instant now) {
        if (identity == null || identity.ref() == null || identity.ref().isBlank()) return blocked(Reason.ABSENT, "absent");
        String audit = sha256(identity.ref());
        if (!Rca2RuntimeContracts.ENVIRONMENT.equals(identity.environment())) return blocked(Reason.WRONG_ENVIRONMENT, audit);
        if (!PURPOSE.equals(identity.purpose())) return blocked(Reason.UNAUTHORIZED_PURPOSE, audit);
        if (!allowedCallers.contains(identity.caller())) return blocked(Reason.UNAUTHORIZED_CALLER, audit);
        if (identity.deleted()) return blocked(Reason.DELETED, audit);
        if (identity.invalidated()) return blocked(Reason.INVALID, audit);
        if (identity.validUntil() == null || !now.isBefore(identity.validUntil())) return blocked(Reason.EXPIRED, audit);
        if (!identity.encryptedAtRest() || !identity.encryptedInTransit()) return blocked(Reason.ENCRYPTION_CONTRACT_FAILED, audit);
        if (identity.ref().matches("user:[0-9]+") || identity.ref().matches("subject:production:.+")) {
            return blocked(Reason.ACTUAL_PRODUCTION_IDENTITY_BLOCKED, audit);
        }
        if (identity.ref().matches("synthetic:[a-z0-9][a-z0-9._-]{2,127}")) {
            return new Decision(true, Reason.ALLOWED_SYNTHETIC, audit);
        }
        if (identity.ref().startsWith("test-account:")) {
            String hash = identity.ref().substring("test-account:".length());
            if (hash.matches("[0-9a-f]{64}") && allowedTestAccountHashes.contains(hash)) {
                return new Decision(true, Reason.ALLOWED_TEST_ACCOUNT, audit);
            }
            return blocked(Reason.MISMATCHED, audit);
        }
        return blocked(Reason.INVALID, audit);
    }

    public String resolveRequestIdentity(Long userId) {
        if (userId == null || userId <= 0) return "anonymous";
        String hash = sha256("user:" + userId);
        return allowedTestAccountHashes.contains(hash) ? "test-account:" + hash : "subject:production:" + hash;
    }

    private static Decision blocked(Reason reason, String audit) { return new Decision(false, reason, audit); }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
