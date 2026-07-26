package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Rca2RuntimeContracts {
    public static final String WORK_START_SHA = "ed5708bd4da12eaea8180043f5cd7f6eb13c3099";
    public static final String CONTRACT_VERSION = "recommendation-runtime-dark-read-v1";
    public static final String QUERY_REGISTRY_VERSION = "recommendation-runtime-dark-read-query-registry-v1";
    public static final String ENVIRONMENT = "ISOLATED_NON_PRODUCTION_RUNTIME";
    public static final String RUNTIME_MODEL = "ASYNC_POST_RESPONSE_SHADOW";
    public static final int MAX_SHADOW_CONCURRENCY = 4;
    public static final int MAX_SHADOW_QUEUE_DEPTH = 100;
    public static final Duration TASK_QUEUE_TIMEOUT = Duration.ofMillis(50);
    public static final Duration MAX_TASK_AGE = Duration.ofMillis(1_000);
    public static final Duration CONNECTION_TIMEOUT = Duration.ofMillis(100);
    public static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    public static final Duration TOTAL_TIMEOUT = Duration.ofMillis(500);
    public static final int INITIAL_TRAFFIC_PERCENT = 0;
    public static final int MAX_PRODUCTION_DARK_READ_PERCENT = 0;
    public static final String PRIMARY_RESULT_AUTHORITY = "CURRENT_P1_P2_ONLY";
    public static final String SHADOW_RESULT_AUTHORITY = "NONE";
    public static final String SHADOW_RESULT_SERVING = "FORBIDDEN";
    public static final String SHADOW_FAILURE_FALLBACK = "KEEP_PRIMARY_RESULT";
    public static final String RETRY_POLICY = "NONE";
    public static final String LATE_RESULT_POLICY = "DISCARD";
    public static final String IDENTITY_MODE = "SYNTHETIC_OR_TEST_ACCOUNT_ONLY";
    public static final String DB_CHANGE = "NONE";
    public static final String SQL_ALLOCATION = "NOT_REQUIRED";
    public static final String PRODUCTION_ACTIVATION = "NOT_AUTHORIZED";
    public static final String AUTHORITY_TRANSFER = "FORBIDDEN";
    public static final Set<String> P1_EXPECTED_GAPS = Set.of(
            "ORDERING_NOT_COMPARABLE", "EVENT_GRAIN_MISSING", "EXPLICIT_PREFERENCE_MISSING",
            "TRANSFORM_POLICY_MISSING", "FINGERPRINT_SEMANTICS_PROTECTED");
    public static final Set<String> P2_MIGRATION_GAPS = Set.of(
            "STALE_UNEXPOSED_ASSIGNMENT_GAP", "OBSERVATION_DEDUPE_GAP");

    private Rca2RuntimeContracts() {}

    public enum Lane { P1, P2 }
    public enum BreakerState { CLOSED, OPEN, HALF_OPEN }
    public enum SubmissionStatus {
        ACCEPTED, FLAG_OFF, TRAFFIC_ZERO, GLOBAL_KILLED, LANE_KILLED, IDENTITY_BLOCKED,
        CIRCUIT_OPEN, QUEUE_REJECTED, RESPONSE_NOT_COMMITTED, INVALID_CONTEXT, EXECUTOR_UNAVAILABLE
    }
    public enum ExecutionStatus { SUCCESS, TIMEOUT, EXCEPTION, LATE_DISCARDED, CANCELLED }
    public enum ComparisonClass {
        MATCH, EXPECTED_GAP, MIGRATION_GAP, RESULT_MISMATCH, CHECKPOINT_MISMATCH,
        LINEAGE_MISMATCH, STALE_CANDIDATE, AUTHORITY_MISMATCH, REDACTION_FAILURE
    }

    public record Checkpoint(
            String opaqueRef,
            long monotonicSequence,
            Instant capturedAtUtc,
            String sourceVersion,
            String schemaVersion) {
        public Checkpoint {
            opaqueRef = required(opaqueRef, "opaqueRef");
            if (monotonicSequence < 0) throw new IllegalArgumentException("monotonicSequence must be nonnegative");
            Objects.requireNonNull(capturedAtUtc, "capturedAtUtc");
            sourceVersion = required(sourceVersion, "sourceVersion");
            schemaVersion = required(schemaVersion, "schemaVersion");
        }
    }

    public record Lineage(
            String fingerprint,
            String candidateVersion,
            String deploymentVersion,
            String artifactSha) {
        public Lineage {
            fingerprint = required(fingerprint, "fingerprint");
            if (!fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("lineage fingerprint must be sha256");
            candidateVersion = required(candidateVersion, "candidateVersion");
            deploymentVersion = required(deploymentVersion, "deploymentVersion");
            artifactSha = required(artifactSha, "artifactSha");
            if (!artifactSha.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("artifactSha must be git sha");
        }
    }

    public record PrimarySnapshot(
            Lane lane,
            String authoritySource,
            String authorityResult,
            String digest,
            int itemCount,
            String cursorDigest,
            Checkpoint checkpoint,
            Lineage lineage,
            long primaryLatencyMillis) {
        public PrimarySnapshot {
            Objects.requireNonNull(lane, "lane");
            authoritySource = required(authoritySource, "authoritySource");
            authorityResult = required(authorityResult, "authorityResult");
            digest = sha256(digest, "digest");
            if (itemCount < 0) throw new IllegalArgumentException("itemCount must be nonnegative");
            cursorDigest = sha256(cursorDigest, "cursorDigest");
            Objects.requireNonNull(checkpoint, "checkpoint");
            Objects.requireNonNull(lineage, "lineage");
            if (primaryLatencyMillis < 0) throw new IllegalArgumentException("primaryLatencyMillis must be nonnegative");
        }
    }

    public record ShadowRequest(
            String hashedRequestRef,
            String identityRef,
            String purpose,
            String caller,
            String environment,
            String flagVersion,
            Instant registeredAt,
            PrimarySnapshot primary) {
        public ShadowRequest {
            hashedRequestRef = sha256(hashedRequestRef, "hashedRequestRef");
            identityRef = required(identityRef, "identityRef");
            purpose = required(purpose, "purpose");
            caller = required(caller, "caller");
            environment = required(environment, "environment");
            flagVersion = required(flagVersion, "flagVersion");
            Objects.requireNonNull(registeredAt, "registeredAt");
            Objects.requireNonNull(primary, "primary");
        }
    }

    public record CandidateResult(
            Lane lane,
            String digest,
            int resultSize,
            Checkpoint checkpoint,
            Lineage lineage,
            boolean stale,
            boolean duplicate,
            boolean empty,
            Set<String> declaredGaps,
            String exposureAuthority,
            long outcomeWindowSeconds,
            Set<String> engagementEvents,
            String fallbackSource,
            boolean oneObservationKeyValid) {
        public CandidateResult {
            Objects.requireNonNull(lane, "lane");
            digest = sha256(digest, "digest");
            if (resultSize < 0) throw new IllegalArgumentException("resultSize must be nonnegative");
            Objects.requireNonNull(checkpoint, "checkpoint");
            Objects.requireNonNull(lineage, "lineage");
            declaredGaps = Set.copyOf(declaredGaps == null ? Set.of() : declaredGaps);
            exposureAuthority = required(exposureAuthority, "exposureAuthority");
            engagementEvents = Set.copyOf(engagementEvents == null ? Set.of() : engagementEvents);
            fallbackSource = required(fallbackSource, "fallbackSource");
        }
    }

    public record ComparisonResult(
            Lane lane,
            ComparisonClass classification,
            boolean discard,
            long measuredLagMillis,
            List<String> inventory) {
        public ComparisonResult {
            Objects.requireNonNull(lane, "lane");
            Objects.requireNonNull(classification, "classification");
            if (measuredLagMillis < 0) throw new IllegalArgumentException("measuredLagMillis must be nonnegative");
            inventory = List.copyOf(inventory == null ? List.of() : inventory);
        }
    }

    public record Evidence(
            String hashedRequestRef,
            Lane lane,
            String contractVersion,
            String queryRegistryVersion,
            String primaryDigest,
            String candidateDigest,
            String classification,
            String sourceCheckpoint,
            String candidateCheckpoint,
            String lineageFingerprint,
            String shadowLatencyBucket,
            String primaryLatencyBucket,
            String timeoutClass,
            String errorClass,
            String flagVersion,
            String deploymentVersion,
            String testedSha) {
        public Evidence {
            hashedRequestRef = sha256(hashedRequestRef, "hashedRequestRef");
            Objects.requireNonNull(lane, "lane");
            contractVersion = required(contractVersion, "contractVersion");
            queryRegistryVersion = required(queryRegistryVersion, "queryRegistryVersion");
            primaryDigest = sha256(primaryDigest, "primaryDigest");
            candidateDigest = sha256(candidateDigest, "candidateDigest");
            classification = required(classification, "classification");
            sourceCheckpoint = required(sourceCheckpoint, "sourceCheckpoint");
            candidateCheckpoint = required(candidateCheckpoint, "candidateCheckpoint");
            lineageFingerprint = sha256(lineageFingerprint, "lineageFingerprint");
            shadowLatencyBucket = required(shadowLatencyBucket, "shadowLatencyBucket");
            primaryLatencyBucket = required(primaryLatencyBucket, "primaryLatencyBucket");
            timeoutClass = required(timeoutClass, "timeoutClass");
            errorClass = required(errorClass, "errorClass");
            flagVersion = required(flagVersion, "flagVersion");
            deploymentVersion = required(deploymentVersion, "deploymentVersion");
            testedSha = required(testedSha, "testedSha");
        }
    }

    public record Deadline(Duration connectionTimeout, Duration readTimeout, Duration totalTimeout) {
        public Deadline {
            Objects.requireNonNull(connectionTimeout, "connectionTimeout");
            Objects.requireNonNull(readTimeout, "readTimeout");
            Objects.requireNonNull(totalTimeout, "totalTimeout");
            if (connectionTimeout.isNegative() || connectionTimeout.isZero()
                    || readTimeout.isNegative() || readTimeout.isZero()
                    || totalTimeout.isNegative() || totalTimeout.isZero()) {
                throw new IllegalArgumentException("timeouts must be positive");
            }
        }
        public static Deadline approved() { return new Deadline(CONNECTION_TIMEOUT, READ_TIMEOUT, TOTAL_TIMEOUT); }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " is required and trimmed");
        }
        return value;
    }

    private static String sha256(String value, String name) {
        value = required(value, name);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be sha256");
        return value;
    }
}
