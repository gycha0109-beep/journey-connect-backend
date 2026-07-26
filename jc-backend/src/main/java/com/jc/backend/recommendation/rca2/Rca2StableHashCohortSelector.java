package com.jc.backend.recommendation.rca2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable, process-independent selector for an allowlisted hashed subject. */
public final class Rca2StableHashCohortSelector {
    public static final String ALGORITHM_VERSION = "rca2-stable-hash-percentage-v1";
    public static final int BUCKET_COUNT = 10_000;
    public static final int MAX_PERCENT_CEILING = 1;

    public enum Status {
        SELECTED, SKIPPED, INVALID_PERCENT, INVALID_HASHED_SUBJECT,
        SALT_VERSION_MISSING, SALT_MATERIAL_MISSING, NOT_ALLOWLISTED,
        PRODUCTION_ENVIRONMENT, SELECTOR_UNAVAILABLE
    }

    public record Decision(boolean selected, Status status, int bucket, String algorithmVersion, String saltVersion) {}

    private final String saltVersion;
    private final String saltMaterialHash;
    private final boolean available;

    public Rca2StableHashCohortSelector(String saltVersion, String saltMaterialHash) {
        this.saltVersion = saltVersion == null ? "" : saltVersion.trim();
        this.saltMaterialHash = saltMaterialHash == null ? "" : saltMaterialHash.trim();
        this.available = !this.saltVersion.isBlank() && this.saltMaterialHash.matches("[0-9a-f]{64}");
    }

    public Decision select(String hashedSubjectRef, int percent, boolean allowlisted, String environment) {
        if (!available) return decision(false, Status.SELECTOR_UNAVAILABLE, -1);
        if (percent < 0 || percent > MAX_PERCENT_CEILING) return decision(false, Status.INVALID_PERCENT, -1);
        if (!Rca2RuntimeContracts.ENVIRONMENT.equals(environment)) {
            return decision(false, Status.PRODUCTION_ENVIRONMENT, -1);
        }
        if (!allowlisted) return decision(false, Status.NOT_ALLOWLISTED, -1);
        if (hashedSubjectRef == null || !hashedSubjectRef.matches("[0-9a-f]{64}")) {
            return decision(false, Status.INVALID_HASHED_SUBJECT, -1);
        }
        if (percent == 0) return decision(false, Status.SKIPPED, stableBucket(hashedSubjectRef));
        int bucket = stableBucket(hashedSubjectRef);
        int selectedBuckets = percent * (BUCKET_COUNT / 100);
        return decision(bucket < selectedBuckets, bucket < selectedBuckets ? Status.SELECTED : Status.SKIPPED, bucket);
    }

    public boolean available() { return available; }
    public String saltVersion() { return saltVersion; }

    private int stableBucket(String hashedSubjectRef) {
        byte[] digest = sha256(ALGORITHM_VERSION + "|" + saltVersion + "|" + saltMaterialHash + "|" + hashedSubjectRef);
        long unsigned = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(digest, 0, 4).getInt());
        return (int) (unsigned % BUCKET_COUNT);
    }

    private Decision decision(boolean selected, Status status, int bucket) {
        return new Decision(selected, status, bucket, ALGORITHM_VERSION, saltVersion.isBlank() ? "missing" : saltVersion);
    }

    public static String hashSubjectRef(String approvedNonproductionSubjectRef) {
        if (approvedNonproductionSubjectRef == null || approvedNonproductionSubjectRef.isBlank()) {
            throw new IllegalArgumentException("approved non-production subject ref is required");
        }
        if (approvedNonproductionSubjectRef.matches("user:[0-9]+")
                || approvedNonproductionSubjectRef.startsWith("subject:production:")) {
            throw new IllegalArgumentException("raw or production identity cannot be cohort input");
        }
        return HexFormat.of().formatHex(sha256("rca2-approved-test-subject|" + approvedNonproductionSubjectRef));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
