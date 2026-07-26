package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class Rca2Op2RuntimeSafetyTest {
    @Test void safeDefaultsLaneAndGlobalKillRemainIndependentOfPrimaryAuthority() {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        var flags = new Rca2FeatureFlagPolicy(Rca2FeatureFlagPolicy.Snapshot.defaultOff(now));
        assertThat(flags.evaluate(Rca2RuntimeContracts.Lane.P1, now).enabled()).isFalse();
        assertThat(flags.evaluate(Rca2RuntimeContracts.Lane.P2, now).trafficPercent()).isZero();
        var kill = new Rca2KillSwitch();
        kill.killLane(Rca2RuntimeContracts.Lane.P1);
        assertThat(kill.laneKilled(Rca2RuntimeContracts.Lane.P1)).isTrue();
        assertThat(kill.laneKilled(Rca2RuntimeContracts.Lane.P2)).isFalse();
        kill.killGlobal();
        assertThat(kill.globalKilled()).isTrue();
        assertThat(Rca2RuntimeContracts.PRIMARY_RESULT_AUTHORITY).isEqualTo("CURRENT_P1_P2_ONLY");
        assertThat(Rca2RuntimeContracts.SHADOW_RESULT_SERVING).isEqualTo("FORBIDDEN");
    }

    @Test void globalDisableCanCancelQueuedTasksWithoutServingCandidateResults() throws Exception {
        CountDownLatch entered = new CountDownLatch(Rca2RuntimeContracts.MAX_SHADOW_CONCURRENCY);
        CountDownLatch release = new CountDownLatch(1);
        List<Rca2RuntimeContracts.ExecutionStatus> statuses = java.util.Collections.synchronizedList(new ArrayList<>());
        try (var executor = new Rca2BoundedExecutor()) {
            for (int i = 0; i < Rca2RuntimeContracts.MAX_SHADOW_CONCURRENCY + 4; i++) {
                executor.submit(() -> { entered.countDown(); release.await(2, TimeUnit.SECONDS); return "candidate-not-served"; },
                        completion -> statuses.add(completion.status()));
            }
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            int cancelled = executor.cancelQueued();
            release.countDown();
            assertThat(executor.awaitIdle(Duration.ofSeconds(3))).isTrue();
            assertThat(cancelled).isGreaterThan(0);
            assertThat(executor.cancelledCount()).isGreaterThanOrEqualTo(cancelled);
            assertThat(statuses).contains(Rca2RuntimeContracts.ExecutionStatus.CANCELLED);
        }
    }

    @Test void rollbackMatrixDoesNotClaimExternalDrillsPassed() {
        var matrix = new Rca2Op2RollbackMatrix().matrix();
        assertThat(matrix).hasSize(7);
        assertThat(matrix.get(Rca2Op2RollbackMatrix.Level.LEVEL_1).drillStatus())
                .isEqualTo(Rca2Op2RollbackMatrix.Status.PASS);
        assertThat(matrix.get(Rca2Op2RollbackMatrix.Level.LEVEL_5).drillStatus())
                .isEqualTo(Rca2Op2RollbackMatrix.Status.NOT_EXECUTED);
        assertThat(matrix.get(Rca2Op2RollbackMatrix.Level.LEVEL_6).drillStatus())
                .isEqualTo(Rca2Op2RollbackMatrix.Status.BLOCKED_EXTERNAL_DEPENDENCY);
        assertThat(matrix.get(Rca2Op2RollbackMatrix.Level.LEVEL_7).drillStatus())
                .isEqualTo(Rca2Op2RollbackMatrix.Status.BLOCKED_EXTERNAL_DEPENDENCY);
    }
}
