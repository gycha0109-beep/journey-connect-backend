package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Rca2BoundedExecutorTest {
    @Test void timeoutCancelsWithoutRetryAndLateQueueEntriesDiscard() throws Exception {
        try (var executor = new Rca2BoundedExecutor()) {
            var timeout = new CountDownLatch(1);
            AtomicInteger attempts = new AtomicInteger();
            assertThat(executor.submit(() -> {
                attempts.incrementAndGet();
                Thread.sleep(5_000L);
                return "late";
            }, result -> { if (result.status() == Rca2RuntimeContracts.ExecutionStatus.TIMEOUT) timeout.countDown(); }))
                    .isTrue();
            assertThat(timeout.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts).hasValue(1);
            assertThat(executor.timeoutCount()).isEqualTo(1);
        }
    }

    @Test void queueIsFiniteRejectsAndNeverRunsOnCallerThread() throws Exception {
        try (var executor = new Rca2BoundedExecutor()) {
            CountDownLatch block = new CountDownLatch(1);
            List<Boolean> accepted = new ArrayList<>();
            for (int i = 0; i < 110; i++) accepted.add(executor.submit(() -> { block.await(); return "x"; }, ignored -> {}));
            assertThat(accepted.stream().filter(Boolean::booleanValue).count()).isLessThanOrEqualTo(104);
            assertThat(accepted).contains(false);
            assertThat(executor.rejectedCount()).isGreaterThan(0);
            block.countDown();
            assertThat(executor.awaitIdle(Duration.ofSeconds(3))).isTrue();
        }
    }
}
