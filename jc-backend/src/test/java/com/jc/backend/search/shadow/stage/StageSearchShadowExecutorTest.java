package com.jc.backend.search.shadow.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.integration.search.v1.SearchShadowExecutionDeadlineV1;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StageSearchShadowExecutorTest {
    @Test
    void dispatchExecutorIsBoundedDaemonNamedAndFailOpenWhenFullOrClosed() throws Exception {
        StageSearchShadowTaskExecutor executor = new StageSearchShadowTaskExecutor(1, 1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicBoolean daemon = new AtomicBoolean();
        try {
            assertThat(executor.submit(() -> {
                threadName.set(Thread.currentThread().getName());
                daemon.set(Thread.currentThread().isDaemon());
                running.countDown();
                await(release);
            }).status()).isEqualTo(StageSearchShadowSubmissionStatus.ACCEPTED);
            assertThat(running.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.submit(() -> await(release)).status())
                    .isEqualTo(StageSearchShadowSubmissionStatus.ACCEPTED);
            assertThat(executor.submit(() -> { }).status())
                    .isEqualTo(StageSearchShadowSubmissionStatus.QUEUE_FULL);
            release.countDown();
            assertThat(executor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(threadName.get()).startsWith("jc-search-shadow-stage-dispatch-");
            assertThat(daemon).isTrue();
        } finally {
            release.countDown();
            executor.close();
        }
        assertThat(executor.submit(() -> { }).status())
                .isEqualTo(StageSearchShadowSubmissionStatus.EXECUTOR_UNAVAILABLE);
    }

    @Test
    void runtimeExecutionUsesWallClockTimeoutAndCancellation() {
        StageBoundedSearchShadowExecutionPort port = new StageBoundedSearchShadowExecutionPort(1, 1);
        try {
            var request = new DefaultStageExploreSearchRuntimeInputProviderFactory(100)
                    .create(new com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView(
                                    "서울", null, 0, 20, java.util.List.of(), java.util.Map.of()),
                            new com.jc.intelligence.integration.search.v1.SearchShadowContextV1(
                                    "request:ip10", "correlation:ip10", "session:ip10",
                                    Instant.parse("2026-07-19T03:00:00Z")))
                    .provide(new com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputContextV1(
                            "correlation:ip10", Instant.parse("2026-07-19T03:00:00Z"),
                            com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1.sha256("request"),
                            com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1.sha256("response")))
                    .executionRequest();
            var outcome = port.execute(execution -> {
                        try {
                            Thread.sleep(500L);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    }, request, new SearchShadowExecutionDeadlineV1(
                            Instant.parse("2026-07-19T03:00:00Z"), Duration.ofMillis(20)));
            assertThat(outcome.status().wireValue()).isEqualTo("timed_out");
            assertThat(port.cancellationCount()).isEqualTo(1L);
        } finally {
            port.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
