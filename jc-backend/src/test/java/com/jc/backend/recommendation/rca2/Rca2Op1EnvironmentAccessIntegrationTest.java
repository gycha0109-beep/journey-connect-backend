package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Rca2Op1EnvironmentAccessIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final String HOST = "candidate.nonprod.journey-connect.internal";
    private static final String SALT = "c".repeat(64);

    @Test void effectiveTrafficZeroBlocksInvocationEvenWhenFlagSnapshotAttemptsOnePercent() throws Exception {
        String identity = "synthetic:effective-zero";
        String hash = Rca2IdentityPolicy.sha256(identity);
        var adapterCalls = new AtomicInteger();
        try (Fixture fixture = fixture(identity, hash, validConfiguration(HOST), readyCredential(),
                allowlist(hash), readySource(), false, 1, adapterCalls)) {
            var request = request(identity);
            String primaryDigest = request.primary().digest();
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request, true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.TRAFFIC_ZERO);
            assertThat(adapterCalls).hasValue(0);
            assertThat(request.primary().digest()).isEqualTo(primaryDigest);
            assertThat(fixture.metrics.total("shadow_cohort_skipped_total")).isEqualTo(1);
        }
    }

    @Test void testFixtureIntegratesEndpointCredentialAllowlistStableCohortAndStubCandidateWithoutServing() throws Exception {
        Selected selected = selectedIdentity();
        var adapterCalls = new AtomicInteger();
        try (Fixture fixture = fixture(selected.identity(), selected.hash(), validConfiguration(HOST), readyCredential(),
                allowlist(selected.hash()), readySource(), true, 1, adapterCalls)) {
            var request = request(selected.identity());
            String primaryDigest = request.primary().digest();
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request, true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.ACCEPTED);
            assertThat(fixture.executor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(adapterCalls).hasValue(1);
            assertThat(request.primary().digest()).isEqualTo(primaryDigest);
            assertThat(fixture.metrics.total("shadow_endpoint_validation_total")).isEqualTo(1);
            assertThat(fixture.metrics.total("shadow_allowlist_lookup_total")).isEqualTo(1);
            assertThat(fixture.metrics.total("shadow_cohort_selected_total")).isEqualTo(1);
            assertThat(fixture.evidence).hasSize(1);
        }
    }

    @Test void productionEndpointExpiredCredentialAndEmptyAllowlistFailClosedWithoutInvocation() throws Exception {
        Selected selected = selectedIdentity();
        var adapterCalls = new AtomicInteger();
        try (Fixture fixture = fixture(selected.identity(), selected.hash(),
                validConfiguration("candidate.prod.journey-connect.internal"), readyCredential(),
                allowlist(selected.hash()), readySource(), true, 1, adapterCalls)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(selected.identity()), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE);
        }
        try (Fixture fixture = fixture(selected.identity(), selected.hash(), validConfiguration(HOST), expiredCredential(),
                allowlist(selected.hash()), readySource(), true, 1, adapterCalls)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(selected.identity()), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE);
        }
        try (Fixture fixture = fixture(selected.identity(), selected.hash(), validConfiguration(HOST), readyCredential(),
                Rca2TestAccountAllowlist.unavailable(), readySource(), true, 1, adapterCalls)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(selected.identity()), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.IDENTITY_BLOCKED);
        }
        assertThat(adapterCalls).hasValue(0);
    }

    @Test void unresolvedCandidateSourceAndFlagOffBlockStubInvocation() throws Exception {
        Selected selected = selectedIdentity();
        var adapterCalls = new AtomicInteger();
        try (Fixture fixture = fixture(selected.identity(), selected.hash(), validConfiguration(HOST), readyCredential(),
                allowlist(selected.hash()), Rca2CandidateSourceDecision.unresolved(), true, 1, adapterCalls)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(selected.identity()), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.EXECUTOR_UNAVAILABLE);
            assertThat(fixture.metrics.total("shadow_candidate_invocation_blocked_total")).isEqualTo(1);
        }
        try (Fixture fixture = fixture(selected.identity(), selected.hash(), validConfiguration(HOST), readyCredential(),
                allowlist(selected.hash()), readySource(), true, 0, adapterCalls)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(selected.identity()), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.TRAFFIC_ZERO);
        }
        assertThat(adapterCalls).hasValue(0);
    }

    private static Fixture fixture(
            String identityRef,
            String hashedSubjectRef,
            Rca2Op1Configuration configuration,
            Rca2WorkloadCredentialProvider credentialProvider,
            Rca2TestAccountAllowlist.Provider allowlistProvider,
            Rca2CandidateSourceDecision source,
            boolean testFixtureMode,
            int flagTraffic,
            AtomicInteger adapterCalls) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var flags = new Rca2FeatureFlagPolicy(new Rca2FeatureFlagPolicy.Snapshot("on", true, true, flagTraffic,
                Rca2RuntimeContracts.ENVIRONMENT, "op1-test-flag-v1", NOW, NOW, NOW.plusSeconds(60), true));
        var kill = new Rca2KillSwitch();
        var identity = new Rca2IdentityPolicy(Set.of(), Set.of("rca2-contract-test"));
        var breakers = new EnumMap<Rca2RuntimeContracts.Lane, Rca2CircuitBreaker>(Rca2RuntimeContracts.Lane.class);
        breakers.put(Rca2RuntimeContracts.Lane.P1, Rca2CircuitBreaker.system());
        breakers.put(Rca2RuntimeContracts.Lane.P2, Rca2CircuitBreaker.system());
        var executor = new Rca2BoundedExecutor();
        var metrics = Rca2Metrics.inMemory();
        var selector = new Rca2StableHashCohortSelector("salt-v1", SALT);
        var endpointPolicy = new Rca2ShadowEndpointPolicy(Set.of(HOST), Set.of(), false);
        var allowlist = new Rca2TestAccountAllowlist();
        var gate = new Rca2EnvironmentAccessGate(configuration, endpointPolicy, credentialProvider, allowlist,
                allowlistProvider, selector, source, metrics, testFixtureMode);
        Rca2CandidateAdapter adapter = (request, deadline) -> {
            adapterCalls.incrementAndGet();
            return candidate(request);
        };
        List<Rca2RuntimeContracts.Evidence> evidence = new CopyOnWriteArrayList<>();
        var orchestrator = new Rca2RuntimeOrchestrator(clock, flags, kill, identity, breakers, executor, adapter,
                new Rca2Comparator(), metrics, new Rca2Redaction(), evidence::add, gate);
        return new Fixture(orchestrator, executor, metrics, evidence);
    }

    private static Rca2Op1Configuration validConfiguration(String host) {
        return new Rca2Op1Configuration(Rca2RuntimeContracts.ENVIRONMENT, false, 0, 0, 1,
                "https://" + host + "/v1/candidates/read", Set.of(host), Set.of(), "jc-rca2-stage1", "",
                "APPROVED_TEST_FIXTURE", "IN_PROCESS", "V1", "TEST_FIXTURE", "TEST_FIXTURE",
                "salt-v1", SALT, false, false);
    }

    private static Rca2WorkloadCredentialProvider readyCredential() {
        var lease = new Rca2WorkloadCredentialProvider.Lease("fixture-token".toCharArray(), "d".repeat(64),
                Rca2WorkloadCredentialProvider.REQUIRED_AUDIENCE, Set.of("candidate:read"),
                NOW.minusSeconds(10), NOW.plusSeconds(600), false);
        return new Rca2NonProductionTestCredentialProvider(lease, instant -> lease);
    }

    private static Rca2WorkloadCredentialProvider expiredCredential() {
        var lease = new Rca2WorkloadCredentialProvider.Lease("fixture-token".toCharArray(), "e".repeat(64),
                Rca2WorkloadCredentialProvider.REQUIRED_AUDIENCE, Set.of("candidate:read"),
                NOW.minusSeconds(600), NOW.minusSeconds(1), false);
        return new Rca2NonProductionTestCredentialProvider(lease, instant -> lease);
    }

    private static Rca2TestAccountAllowlist.Provider allowlist(String hash) {
        var entry = new Rca2TestAccountAllowlist.Entry(hash, Rca2TestAccountAllowlist.PURPOSE,
                Rca2TestAccountAllowlist.ENVIRONMENT, NOW.minusSeconds(60), NOW.plusSeconds(600),
                false, false, "OP1_INTEGRATION_FIXTURE");
        return new Rca2TestAccountAllowlist.InMemoryNonProductionFixture(List.of(entry));
    }

    private static Rca2CandidateSourceDecision readySource() {
        return new Rca2CandidateSourceDecision("APPROVED_TEST_FIXTURE",
                Rca2CandidateSourceDecision.Protocol.IN_PROCESS, "V1", "INTELLIGENCE",
                true, true, false, false);
    }

    private static Selected selectedIdentity() {
        var selector = new Rca2StableHashCohortSelector("salt-v1", SALT);
        for (int index = 0; index < 100_000; index++) {
            String identity = "synthetic:op1-selected-" + index;
            String hash = Rca2IdentityPolicy.sha256(identity);
            if (selector.select(hash, 1, true, Rca2RuntimeContracts.ENVIRONMENT).selected()) {
                return new Selected(identity, hash);
            }
        }
        throw new IllegalStateException("unable to find deterministic 1% fixture");
    }

    private static Rca2RuntimeContracts.ShadowRequest request(String identity) {
        String digest = "a".repeat(64);
        var checkpoint = new Rca2RuntimeContracts.Checkpoint("opaque-checkpoint", 10, NOW, "v", "s");
        var lineage = new Rca2RuntimeContracts.Lineage("b".repeat(64), "candidate-v1", "deploy-v1",
                Rca2RuntimeContracts.WORK_START_SHA);
        var primary = new Rca2RuntimeContracts.PrimarySnapshot(Rca2RuntimeContracts.Lane.P1,
                "RecommendationP1ProfileSource", "recommendation_p1_profile_snapshot", digest, 2,
                "c".repeat(64), checkpoint, lineage, 4);
        return new Rca2RuntimeContracts.ShadowRequest("f".repeat(64), identity, Rca2IdentityPolicy.PURPOSE,
                "rca2-contract-test", Rca2RuntimeContracts.ENVIRONMENT, "flag-v1", NOW, primary);
    }

    private static Rca2RuntimeContracts.CandidateResult candidate(Rca2RuntimeContracts.ShadowRequest request) {
        return new Rca2RuntimeContracts.CandidateResult(request.primary().lane(), request.primary().digest(),
                request.primary().itemCount(), request.primary().checkpoint(), request.primary().lineage(), false,
                false, false, Rca2RuntimeContracts.P1_EXPECTED_GAPS,
                "recommendation_p2_experiment_exposure", 604_800L,
                Set.of("click", "like", "save", "share"), "BOUND_RECOMMENDATION_RUN_ONLY", true);
    }

    private record Selected(String identity, String hash) {}
    private record Fixture(Rca2RuntimeOrchestrator orchestrator, Rca2BoundedExecutor executor,
            Rca2Metrics metrics, List<Rca2RuntimeContracts.Evidence> evidence) implements AutoCloseable {
        @Override public void close() { executor.close(); }
    }
}
