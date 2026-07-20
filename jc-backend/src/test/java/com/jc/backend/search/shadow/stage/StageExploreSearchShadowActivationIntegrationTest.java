package com.jc.backend.search.shadow.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.backend.search.shadow.ExploreSearchShadowBridge;
import com.jc.backend.search.shadow.SearchShadowBackendConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;

class StageExploreSearchShadowActivationIntegrationTest {
    private final ApplicationContextRunner activeRunner = new ApplicationContextRunner()
            .withUserConfiguration(SearchShadowBackendConfiguration.class, StageSearchShadowConfiguration.class)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("search-shadow-test"))
            .withPropertyValues(
                    "search.shadow.stage.explicit-allow=true",
                    "search.shadow.stage.mode=test_only",
                    "search.shadow.stage.sample-basis-points=10000",
                    "search.shadow.stage.timeout-millis=200",
                    "search.shadow.stage.queue-capacity=8",
                    "search.shadow.stage.max-concurrency=2");

    @Test
    void activeTestProfileExecutesRealSearchRuntimeAndNeverMutatesLegacyResponse() {
        activeRunner.run(context -> {
            ExploreSearchShadowBridge bridge = context.getBean(ExploreSearchShadowBridge.class);
            PageResponse<PostDtos.Summary> response = response();
            PageResponse<PostDtos.Summary> responseIdentity = response;
            List<PostDtos.Summary> itemIdentity = response.items();
            List<PostDtos.Summary> originalItems = List.copyOf(response.items());

            bridge.afterExplore("  서울   여행  ", "서울", PageRequest.of(0, 20), response);

            StageSearchShadowTaskExecutor executor = context.getBean(StageSearchShadowTaskExecutor.class);
            assertThat(executor.awaitIdle(Duration.ofSeconds(2))).isTrue();
            InMemoryStageSearchCatalog catalog = context.getBean(InMemoryStageSearchCatalog.class);
            InMemoryStageSearchShadowComparisonLogPort recorder =
                    context.getBean(InMemoryStageSearchShadowComparisonLogPort.class);
            StageBoundedSearchShadowExecutionPort runtimePort =
                    context.getBean(StageBoundedSearchShadowExecutionPort.class);

            assertThat(response).isSameAs(responseIdentity);
            assertThat(response.items()).isEqualTo(originalItems);
            assertThat(response.items()).isSameAs(itemIdentity);
            assertThat(response.page()).isZero();
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.totalElements()).isEqualTo(1L);
            assertThat(catalog.retrievalInvocationCount()).isEqualTo(1L);
            assertThat(catalog.rankingInvocationCount()).isEqualTo(1L);
            assertThat(runtimePort.invocationCount()).isEqualTo(1L);
            assertThat(recorder.records()).hasSize(1);
            String safeRecord = recorder.records().getFirst().toString();
            assertThat(safeRecord).doesNotContain("서울", "correlation:ip10-stage", "session:ip10-stage");
            assertThat(executor.acceptedCount()).isEqualTo(1L);
        });
    }

    @Test
    void sampleZeroCausesNoDispatchRuntimeOrEvidence() {
        activeRunner.withPropertyValues("search.shadow.stage.sample-basis-points=0")
                .run(context -> {
                    PageResponse<PostDtos.Summary> response = response();
                    PageResponse<PostDtos.Summary> responseIdentity = response;
                    context.getBean(ExploreSearchShadowBridge.class)
                            .afterExplore("서울", "서울", PageRequest.of(0, 20), response);
                    StageSearchShadowTaskExecutor executor = context.getBean(StageSearchShadowTaskExecutor.class);
                    assertThat(executor.awaitIdle(Duration.ofMillis(100))).isTrue();
                    assertThat(executor.acceptedCount()).isZero();
                    assertThat(context.getBean(InMemoryStageSearchCatalog.class).retrievalInvocationCount()).isZero();
                    assertThat(context.getBean(StageBoundedSearchShadowExecutionPort.class).invocationCount()).isZero();
                    assertThat(context.getBean(InMemoryStageSearchShadowComparisonLogPort.class).attemptCount()).isZero();
                    assertThat(response).isSameAs(responseIdentity);
                });
    }

    private static PageResponse<PostDtos.Summary> response() {
        return new PageResponse<>(List.of(new PostDtos.Summary(
                101L, "legacy-title", "KR-11", "서울", null, 3L, 2L, 1L,
                new PostDtos.Author(7L, "legacy-author", null),
                Instant.parse("2026-07-19T00:00:00Z"))), 0, 20, 1L, 1, true);
    }
}
