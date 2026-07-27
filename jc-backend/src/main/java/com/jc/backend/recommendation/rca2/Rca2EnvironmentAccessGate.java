package com.jc.backend.recommendation.rca2;

import java.time.Instant;
import java.util.Objects;

/**
 * Ordered OP-1 gate: effective-zero -> endpoint -> credential -> allowlist -> stable cohort -> candidate source.
 * It never performs network I/O and never mutates the primary response.
 */
public final class Rca2EnvironmentAccessGate {
    public enum Status {
        READY, EFFECTIVE_TRAFFIC_ZERO, CONFIGURATION_BLOCKED, ENDPOINT_BLOCKED,
        CREDENTIAL_BLOCKED, ALLOWLIST_BLOCKED, COHORT_SKIPPED,
        CANDIDATE_SOURCE_UNRESOLVED
    }

    public record Decision(
            boolean allowed,
            Status status,
            Rca2RuntimeContracts.SubmissionStatus submissionStatus,
            String hashedSubjectRef,
            int bucket) {}

    private final Rca2Op1Configuration configuration;
    private final Rca2ShadowEndpointPolicy endpointPolicy;
    private final Rca2WorkloadCredentialProvider credentialProvider;
    private final Rca2TestAccountAllowlist allowlist;
    private final Rca2TestAccountAllowlist.Provider allowlistProvider;
    private final Rca2StableHashCohortSelector cohortSelector;
    private final Rca2CandidateSourceDecision candidateSource;
    private final Rca2Metrics metrics;
    private final boolean testFixtureMode;
    private final boolean legacyPassthrough;

    public Rca2EnvironmentAccessGate(
            Rca2Op1Configuration configuration,
            Rca2ShadowEndpointPolicy endpointPolicy,
            Rca2WorkloadCredentialProvider credentialProvider,
            Rca2TestAccountAllowlist allowlist,
            Rca2TestAccountAllowlist.Provider allowlistProvider,
            Rca2StableHashCohortSelector cohortSelector,
            Rca2CandidateSourceDecision candidateSource,
            Rca2Metrics metrics,
            boolean testFixtureMode) {
        this(configuration, endpointPolicy, credentialProvider, allowlist, allowlistProvider,
                cohortSelector, candidateSource, metrics, testFixtureMode, false);
    }

    private Rca2EnvironmentAccessGate(
            Rca2Op1Configuration configuration,
            Rca2ShadowEndpointPolicy endpointPolicy,
            Rca2WorkloadCredentialProvider credentialProvider,
            Rca2TestAccountAllowlist allowlist,
            Rca2TestAccountAllowlist.Provider allowlistProvider,
            Rca2StableHashCohortSelector cohortSelector,
            Rca2CandidateSourceDecision candidateSource,
            Rca2Metrics metrics,
            boolean testFixtureMode,
            boolean legacyPassthrough) {
        this.configuration = configuration;
        this.endpointPolicy = endpointPolicy;
        this.credentialProvider = credentialProvider;
        this.allowlist = allowlist;
        this.allowlistProvider = allowlistProvider;
        this.cohortSelector = cohortSelector;
        this.candidateSource = candidateSource;
        this.metrics = metrics;
        this.testFixtureMode = testFixtureMode;
        this.legacyPassthrough = legacyPassthrough;
    }

    public Decision evaluate(
            Rca2RuntimeContracts.ShadowRequest request,
            String hashedSubjectRef,
            int configuredPercent,
            Instant now,
            Rca2RuntimeContracts.BreakerState breakerState) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        if (legacyPassthrough) return ready(hashedSubjectRef, -1);
        var lane = request.primary().lane();
        if (configuration == null || configuredPercent < 0 || configuredPercent > 1) {
            return blocked(Status.CONFIGURATION_BLOCKED, Rca2RuntimeContracts.SubmissionStatus.FLAG_OFF,
                    hashedSubjectRef, -1);
        }
        if (!testFixtureMode && configuration.effectiveTrafficPercent() == 0) {
            metrics.increment("shadow_cohort_skipped_total", lane, "effective_traffic_zero", breakerState);
            metrics.increment("traffic_skipped_count", lane, "effective_traffic_zero", breakerState);
            return blocked(Status.EFFECTIVE_TRAFFIC_ZERO, Rca2RuntimeContracts.SubmissionStatus.TRAFFIC_ZERO,
                    hashedSubjectRef, -1);
        }
        if (!testFixtureMode && (configuration.configuredTrafficPercent() != 0 || configuredPercent != 0)) {
            return blocked(Status.CONFIGURATION_BLOCKED, Rca2RuntimeContracts.SubmissionStatus.FLAG_OFF,
                    hashedSubjectRef, -1);
        }
        if (configuredPercent == 0) {
            metrics.increment("shadow_cohort_skipped_total", lane, "configured_traffic_zero", breakerState);
            metrics.increment("traffic_skipped_count", lane, "configured_traffic_zero", breakerState);
            return blocked(Status.EFFECTIVE_TRAFFIC_ZERO, Rca2RuntimeContracts.SubmissionStatus.TRAFFIC_ZERO,
                    hashedSubjectRef, -1);
        }

        metrics.increment("shadow_endpoint_validation_total", lane, "validation", breakerState);
        var endpoint = endpointPolicy.validate(configuration.endpoint(), configuration.nonproductionNamespace(),
                "UNRESOLVED".equals(configuration.databaseRoute()) ? "" : configuration.databaseRoute());
        if (!endpoint.allowed()) {
            metrics.increment("shadow_endpoint_blocked_total", lane, endpoint.rejection().name().toLowerCase(), breakerState);
            return blocked(Status.ENDPOINT_BLOCKED, Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE,
                    hashedSubjectRef, -1);
        }

        var lease = credentialProvider.current(now);
        if (lease.isEmpty()) {
            metrics.increment("shadow_credential_unavailable_total", lane, "missing", breakerState);
            return blocked(Status.CREDENTIAL_BLOCKED, Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE,
                    hashedSubjectRef, -1);
        }
        var credentialStatus = Rca2WorkloadCredentialProvider.validate(lease.get(), now);
        if (credentialStatus == Rca2WorkloadCredentialProvider.Status.STALE) {
            metrics.increment("shadow_credential_refresh_total", lane, "refresh", breakerState);
            var refresh = credentialProvider.refresh(now);
            if (refresh.status() != Rca2WorkloadCredentialProvider.Status.READY) {
                metrics.increment("shadow_credential_refresh_failure_total", lane,
                        refresh.status().name().toLowerCase(), breakerState);
                return blocked(Status.CREDENTIAL_BLOCKED, Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE,
                        hashedSubjectRef, -1);
            }
            lease = credentialProvider.current(now);
            credentialStatus = lease.map(value -> Rca2WorkloadCredentialProvider.validate(value, now))
                    .orElse(Rca2WorkloadCredentialProvider.Status.MISSING);
        }
        if (credentialStatus != Rca2WorkloadCredentialProvider.Status.READY) {
            metrics.increment("shadow_credential_unavailable_total", lane,
                    credentialStatus.name().toLowerCase(), breakerState);
            return blocked(Status.CREDENTIAL_BLOCKED, Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE,
                    hashedSubjectRef, -1);
        }

        metrics.increment("shadow_allowlist_lookup_total", lane, "lookup", breakerState);
        var allowlistDecision = allowlist.evaluate(allowlistProvider, hashedSubjectRef, request.purpose(),
                request.environment(), now);
        if (!allowlistDecision.allowed()) {
            metrics.increment("shadow_allowlist_denied_total", lane,
                    allowlistDecision.status().name().toLowerCase(), breakerState);
            return blocked(Status.ALLOWLIST_BLOCKED, Rca2RuntimeContracts.SubmissionStatus.IDENTITY_BLOCKED,
                    hashedSubjectRef, -1);
        }

        var cohort = cohortSelector.select(hashedSubjectRef, configuredPercent, true, request.environment());
        if (!cohort.selected()) {
            metrics.increment("shadow_cohort_skipped_total", lane, cohort.status().name().toLowerCase(), breakerState);
            metrics.increment("traffic_skipped_count", lane, cohort.status().name().toLowerCase(), breakerState);
            return blocked(Status.COHORT_SKIPPED, Rca2RuntimeContracts.SubmissionStatus.FLAG_OFF,
                    hashedSubjectRef, cohort.bucket());
        }
        metrics.increment("shadow_cohort_selected_total", lane, "selected", breakerState);
        metrics.increment("traffic_selected_count", lane, "selected", breakerState);

        if (!candidateSource.ready()) {
            metrics.increment("shadow_candidate_invocation_blocked_total", lane, "source_unresolved", breakerState);
            return blocked(Status.CANDIDATE_SOURCE_UNRESOLVED,
                    Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE,
                    hashedSubjectRef, cohort.bucket());
        }
        return ready(hashedSubjectRef, cohort.bucket());
    }

    private static Decision ready(String hash, int bucket) {
        return new Decision(true, Status.READY, Rca2RuntimeContracts.SubmissionStatus.ACCEPTED, hash, bucket);
    }

    private static Decision blocked(Status status, Rca2RuntimeContracts.SubmissionStatus submissionStatus,
            String hash, int bucket) {
        return new Decision(false, status, submissionStatus, hash == null ? "missing" : hash, bucket);
    }

    public static Rca2EnvironmentAccessGate legacyPassthrough() {
        return new Rca2EnvironmentAccessGate(null, null, null, null, null, null, null, null,
                true, true);
    }
}
