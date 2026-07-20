package com.jc.backend.search.shadow.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageSearchRuntimeInputProviderTest {
    private static final Instant TIME = Instant.parse("2026-07-19T03:00:00Z");
    private final DefaultStageExploreSearchRuntimeInputProviderFactory factory =
            new DefaultStageExploreSearchRuntimeInputProviderFactory(100);

    @Test
    void providerBuildsDeterministicSearchContractInputFromLegacyRequestOnly() {
        LegacyExploreRequestView request = new LegacyExploreRequestView(
                "  서울   여행  ", "서울", 0, 20, List.of(), Map.of());
        SearchShadowContextV1 shadow = new SearchShadowContextV1(
                "request:ip10", "correlation:ip10", "session:ip10", TIME);
        SearchShadowRuntimeInputContextV1 context = inputContext();

        var first = factory.create(request, shadow).provide(context);
        var second = factory.create(request, shadow).provide(context);

        assertThat(first.status()).isEqualTo(SearchShadowRuntimeInputStatus.AVAILABLE);
        assertThat(first).isEqualTo(second);
        assertThat(first.executionRequest().searchRequest().query().normalizedQuery()).isEqualTo("서울 여행");
        assertThat(first.executionRequest().searchRequest().filters()).hasSize(1);
        assertThat(first.executionRequest().searchRequest().pageRequest().pageSize()).isEqualTo(20);
        assertThat(first.runtimeInputFingerprint()).isEqualTo(
                SearchShadowFingerprintV1.runtimeInput(first.executionRequest()));
    }

    @Test
    void unsupportedOffsetSortAndInvalidPageAreTypedAndNeverFabricated() {
        SearchShadowContextV1 shadow = new SearchShadowContextV1(
                "request:ip10", "correlation:ip10", "session:ip10", TIME);
        var later = factory.create(new LegacyExploreRequestView(null, null, 1, 20, List.of(), Map.of()), shadow)
                .provide(inputContext());
        assertThat(later.status()).isEqualTo(SearchShadowRuntimeInputStatus.UNSUPPORTED);
        assertThat(later.executionRequest()).isNull();

        var sorted = factory.create(new LegacyExploreRequestView(null, null, 0, 20,
                        List.of(new com.jc.intelligence.compat.search.explore.v1.LegacyExploreSortOrderView(
                                "createdAt",
                                com.jc.intelligence.compat.search.explore.v1.LegacyExploreSortDirection.DESC)), Map.of()), shadow)
                .provide(inputContext());
        assertThat(sorted.status()).isEqualTo(SearchShadowRuntimeInputStatus.UNSUPPORTED);

        var invalid = factory.create(new LegacyExploreRequestView(null, null, -1, 20, List.of(), Map.of()), shadow)
                .provide(inputContext());
        assertThat(invalid.status()).isEqualTo(SearchShadowRuntimeInputStatus.INVALID);
    }

    private static SearchShadowRuntimeInputContextV1 inputContext() {
        return new SearchShadowRuntimeInputContextV1("correlation:ip10", TIME,
                SearchShadowFingerprintV1.sha256("request"), SearchShadowFingerprintV1.sha256("response"));
    }
}
