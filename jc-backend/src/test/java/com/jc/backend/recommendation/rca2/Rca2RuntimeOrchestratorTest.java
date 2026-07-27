package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.CursorPageResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class Rca2RuntimeOrchestratorTest {
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void successMismatchCheckpointLineageTimeoutExceptionAndIdentityBlockNeverMutatePrimary() throws Exception {
        var response = ApiResponse.ok(CursorPageResponse.of(List.of("a", "b"), "cursor", true));
        byte[] before = mapper.writeValueAsBytes(response);
        runScenario(adapterMatching(), request(Rca2RuntimeContracts.Lane.P1), "shadow_success_count");
        runScenario((request, deadline) -> candidate(request, "f".repeat(64), false, false),
                request(Rca2RuntimeContracts.Lane.P1), "p1_result_mismatch_count");
        runScenario((request, deadline) -> {
            var candidate = candidate(request, request.primary().digest(), false, false);
            var bad = new Rca2RuntimeContracts.Checkpoint("other", 0, NOW, "v", "s");
            return new Rca2RuntimeContracts.CandidateResult(candidate.lane(), candidate.digest(), candidate.resultSize(),
                    bad, candidate.lineage(), false, false, false, Set.of(), candidate.exposureAuthority(),
                    candidate.outcomeWindowSeconds(), candidate.engagementEvents(), candidate.fallbackSource(), true);
        }, request(Rca2RuntimeContracts.Lane.P1), "checkpoint_mismatch_count");
        runScenario((request, deadline) -> {
            var candidate = candidate(request, request.primary().digest(), false, false);
            var bad = new Rca2RuntimeContracts.Lineage("f".repeat(64), "v", "d", Rca2RuntimeContracts.WORK_START_SHA);
            return new Rca2RuntimeContracts.CandidateResult(candidate.lane(), candidate.digest(), candidate.resultSize(),
                    candidate.checkpoint(), bad, false, false, false, Set.of(), candidate.exposureAuthority(),
                    candidate.outcomeWindowSeconds(), candidate.engagementEvents(), candidate.fallbackSource(), true);
        }, request(Rca2RuntimeContracts.Lane.P1), "lineage_mismatch_count");
        runScenario((request, deadline) -> { Thread.sleep(5_000L); return candidate(request, request.primary().digest(), false, false); },
                request(Rca2RuntimeContracts.Lane.P1), "shadow_timeout_count");
        runScenario((request, deadline) -> { throw new IllegalStateException("safe failure"); },
                request(Rca2RuntimeContracts.Lane.P2), "shadow_exception_count");
        try (Fixture fixture = fixture(adapterMatching())) {
            var blocked = request(Rca2RuntimeContracts.Lane.P1, "user:42");
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(blocked, true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.IDENTITY_BLOCKED);
            assertThat(fixture.metrics.total("identity_blocked_count")).isEqualTo(1);
        }
        byte[] after = mapper.writeValueAsBytes(response);
        assertThat(after).isEqualTo(before);
    }

    @Test void op2ExecutorTaskCancellationAndCheckpointMetricsAreWired() throws Exception {
        Rca2CandidateAdapter laggedCandidate = (request, deadline) -> {
            var base = candidate(request, request.primary().digest(), false, false);
            var checkpoint = new Rca2RuntimeContracts.Checkpoint(
                    request.primary().checkpoint().opaqueRef(),
                    request.primary().checkpoint().monotonicSequence(),
                    NOW.minusMillis(25),
                    request.primary().checkpoint().sourceVersion(),
                    request.primary().checkpoint().schemaVersion());
            return new Rca2RuntimeContracts.CandidateResult(base.lane(), base.digest(), base.resultSize(),
                    checkpoint, base.lineage(), false, false, false, base.declaredGaps(), base.exposureAuthority(),
                    base.outcomeWindowSeconds(), base.engagementEvents(), base.fallbackSource(), true);
        };
        try (Fixture fixture = fixture(laggedCandidate)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(Rca2RuntimeContracts.Lane.P1), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.ACCEPTED);
            assertThat(fixture.executor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(fixture.metrics.sampleCount("executor_active_count")).isGreaterThanOrEqualTo(2);
            assertThat(fixture.metrics.sampleCount("executor_queue_depth")).isGreaterThanOrEqualTo(2);
            assertThat(fixture.metrics.sampleCount("shadow_task_age_ms")).isEqualTo(1);
            assertThat(fixture.metrics.sampleCount("checkpoint_lag_ms")).isEqualTo(1);
            assertThat(fixture.metrics.total("checkpoint_lag_ms")).isEqualTo(25);
        }
        try (Fixture fixture = fixture((request, deadline) -> {
            Thread.sleep(5_000L);
            return candidate(request, request.primary().digest(), false, false);
        })) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(Rca2RuntimeContracts.Lane.P1), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.ACCEPTED);
            assertThat(fixture.executor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(fixture.metrics.total("shadow_cancelled_count")).isEqualTo(1);
            assertThat(fixture.metrics.total("shadow_timeout_count")).isEqualTo(1);
        }
    }

    @Test void responseMustBeCommittedGlobalAndLaneKillAndTrafficZeroPreventExecution() {
        try (Fixture fixture = fixture(adapterMatching())) {
            var request = request(Rca2RuntimeContracts.Lane.P1);
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request, false))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.RESPONSE_NOT_COMMITTED);
            fixture.kill.killGlobal();
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request, true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.GLOBAL_KILLED);
            fixture.kill.restoreGlobal(); fixture.kill.killLane(Rca2RuntimeContracts.Lane.P1);
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request, true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.LANE_KILLED);
        }
        try (Fixture fixture = fixture(adapterMatching(), 0)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request(Rca2RuntimeContracts.Lane.P1), true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.TRAFFIC_ZERO);
        }
    }

    @Test void noWriteNoEventNoResponseMutationGuardBlocksEveryAttempt() {
        var metrics = Rca2Metrics.inMemory();
        var guard = new Rca2SideEffectGuard(metrics);
        assertThatThrownBy(() -> guard.databaseWrite(Rca2RuntimeContracts.Lane.P1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guard.cacheWrite(Rca2RuntimeContracts.Lane.P1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guard.eventEmission(Rca2RuntimeContracts.Lane.P2)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guard.notification(Rca2RuntimeContracts.Lane.P2)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guard.rankingFeedback(Rca2RuntimeContracts.Lane.P2)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guard.responseMutation(Rca2RuntimeContracts.Lane.P1)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(metrics.total("shadow_write_attempt_blocked_count")).isEqualTo(2);
        assertThat(metrics.total("shadow_event_attempt_blocked_count")).isEqualTo(3);
        assertThat(metrics.total("shadow_response_mutation_blocked_count")).isEqualTo(1);
    }

    @Test void metricsContainExactlyRequiredLowCardinalityInventory() {
        var metrics = Rca2Metrics.inMemory();
        assertThat(metrics.definitions()).containsKeys(Rca2Metrics.REQUIRED).containsKeys(Rca2Metrics.OP2_BACKLOG);
        assertThat(Rca2Metrics.ALLOWED_LABELS).containsExactlyInAnyOrder(
                "environment", "lane", "result_class", "breaker_state");
    }

    private void runScenario(Rca2CandidateAdapter adapter, Rca2RuntimeContracts.ShadowRequest request,
            String expectedMetric) throws Exception {
        try (Fixture fixture = fixture(adapter)) {
            assertThat(fixture.orchestrator.submitAfterResponseCommitted(request, true))
                    .isEqualTo(Rca2RuntimeContracts.SubmissionStatus.ACCEPTED);
            assertThat(fixture.executor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(fixture.metrics.total(expectedMetric)).isGreaterThanOrEqualTo(1);
        }
    }

    private static Rca2CandidateAdapter adapterMatching() {
        return (request, deadline) -> candidate(request, request.primary().digest(), false, false);
    }

    private static Rca2RuntimeContracts.CandidateResult candidate(Rca2RuntimeContracts.ShadowRequest request,
            String digest, boolean stale, boolean duplicate) {
        return new Rca2RuntimeContracts.CandidateResult(request.primary().lane(), digest,
                request.primary().itemCount(), request.primary().checkpoint(), request.primary().lineage(), stale,
                duplicate, false, request.primary().lane() == Rca2RuntimeContracts.Lane.P1
                        ? Rca2RuntimeContracts.P1_EXPECTED_GAPS : Rca2RuntimeContracts.P2_MIGRATION_GAPS,
                "recommendation_p2_experiment_exposure", 604_800L,
                Set.of("click", "like", "save", "share"), "BOUND_RECOMMENDATION_RUN_ONLY", true);
    }

    private static Rca2RuntimeContracts.ShadowRequest request(Rca2RuntimeContracts.Lane lane) {
        return request(lane, "synthetic:fixture-a");
    }

    private static Rca2RuntimeContracts.ShadowRequest request(Rca2RuntimeContracts.Lane lane, String identity) {
        String digest = "a".repeat(64);
        var checkpoint = new Rca2RuntimeContracts.Checkpoint("opaque-checkpoint", 10, NOW, "v", "s");
        var lineage = new Rca2RuntimeContracts.Lineage("b".repeat(64), "candidate-v1", "deploy-v1",
                Rca2RuntimeContracts.WORK_START_SHA);
        var primary = new Rca2RuntimeContracts.PrimarySnapshot(lane,
                lane == Rca2RuntimeContracts.Lane.P1 ? "RecommendationP1ProfileSource" : "RecommendationP2ObservationSource",
                lane == Rca2RuntimeContracts.Lane.P1 ? "recommendation_p1_profile_snapshot" : "recommendation-evaluation-dataset-v1",
                digest, 2, "c".repeat(64), checkpoint, lineage, 4);
        return new Rca2RuntimeContracts.ShadowRequest("d".repeat(64), identity, Rca2IdentityPolicy.PURPOSE,
                "rca2-contract-test", Rca2RuntimeContracts.ENVIRONMENT, "flag-v1", NOW, primary);
    }

    private static Fixture fixture(Rca2CandidateAdapter adapter) { return fixture(adapter, 100); }
    private static Fixture fixture(Rca2CandidateAdapter adapter, int traffic) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var flags = new Rca2FeatureFlagPolicy(new Rca2FeatureFlagPolicy.Snapshot("on", true, true, traffic,
                Rca2RuntimeContracts.ENVIRONMENT, "flag-v1", NOW, NOW, NOW.plusSeconds(60), true));
        var kill = new Rca2KillSwitch();
        var identity = new Rca2IdentityPolicy(Set.of(), Set.of("rca2-contract-test"));
        var breakers = new EnumMap<Rca2RuntimeContracts.Lane, Rca2CircuitBreaker>(Rca2RuntimeContracts.Lane.class);
        breakers.put(Rca2RuntimeContracts.Lane.P1, Rca2CircuitBreaker.system());
        breakers.put(Rca2RuntimeContracts.Lane.P2, Rca2CircuitBreaker.system());
        var executor = new Rca2BoundedExecutor();
        var metrics = Rca2Metrics.inMemory();
        List<Rca2RuntimeContracts.Evidence> evidence = new CopyOnWriteArrayList<>();
        var orchestrator = new Rca2RuntimeOrchestrator(clock, flags, kill, identity, breakers, executor, adapter,
                new Rca2Comparator(), metrics, new Rca2Redaction(), evidence::add);
        return new Fixture(orchestrator, executor, metrics, kill, evidence);
    }

    private record Fixture(Rca2RuntimeOrchestrator orchestrator, Rca2BoundedExecutor executor,
            Rca2Metrics metrics, Rca2KillSwitch kill, List<Rca2RuntimeContracts.Evidence> evidence)
            implements AutoCloseable { @Override public void close() { executor.close(); } }
}
