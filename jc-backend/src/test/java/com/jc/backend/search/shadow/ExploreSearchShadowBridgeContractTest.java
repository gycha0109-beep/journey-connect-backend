package com.jc.backend.search.shadow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.wiring.search.v1.NoOpSearchShadowHook;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ExploreSearchShadowBridgeContractTest {

    @Test
    void disabledBridgeIsACompleteNoOp() {
        PageResponse<PostDtos.Summary> response = response();
        ExploreSearchShadowBridge bridge = new DisabledExploreSearchShadowBridge();
        assertDoesNotThrow(() -> bridge.afterExplore("query", "region", PageRequest.of(0, 20), response));
        assertSame(response, response);
    }

    @Test
    void controlledBridgeBuildsApprovedCompatibilityRequestAndDiscardsReceipt() {
        Instant referenceTime = Instant.parse("2026-07-19T01:00:00Z");
        ExploreShadowRequestContext context = new ExploreShadowRequestContext(
                "request:ip9-test",
                "correlation:ip9-test",
                "session:ip9-test",
                referenceTime,
                referenceTime.plusMillis(1),
                new ProducerBuildId("ip9-test-build-1"));
        DefaultExploreShadowHookRequestFactory factory =
                new DefaultExploreShadowHookRequestFactory(() -> context);
        AtomicReference<SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>>> captured = new AtomicReference<>();
        DefaultExploreSearchShadowBridge bridge = new DefaultExploreSearchShadowBridge(
                factory,
                request -> {
                    captured.set(request);
                    return new NoOpSearchShadowHook<PageResponse<PostDtos.Summary>>().dispatch(request);
                });
        PageResponse<PostDtos.Summary> response = response();
        Pageable pageable = PageRequest.of(1, 10);

        bridge.afterExplore("  서울  ", "KR-SEOUL", pageable, response);

        SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request = captured.get();
        assertSame(response, request.legacyResponse());
        assertEquals("  서울  ", request.legacyRequest().keyword());
        assertEquals("KR-SEOUL", request.legacyRequest().region());
        assertEquals(1, request.legacyRequest().page());
        assertEquals(10, request.legacyRequest().size());
        assertEquals(response.items().size(), request.legacyPage().items().size());
        assertEquals(context.correlationId(), request.shadowContext().correlationId());
    }

    @Test
    void requestFactoryAndHookRuntimeFailuresAreContainedButErrorsAreNotSwallowed() {
        PageResponse<PostDtos.Summary> response = response();
        Pageable pageable = PageRequest.of(0, 20);
        AtomicInteger hookCalls = new AtomicInteger();

        DefaultExploreSearchShadowBridge factoryFailure = new DefaultExploreSearchShadowBridge(
                (keyword, region, requestedPage, legacyResponse) -> {
                    throw new IllegalStateException("factory_failure");
                },
                request -> {
                    hookCalls.incrementAndGet();
                    return null;
                });
        assertDoesNotThrow(() -> factoryFailure.afterExplore(null, null, pageable, response));
        assertEquals(0, hookCalls.get());

        DefaultExploreShadowHookRequestFactory validFactory = new DefaultExploreShadowHookRequestFactory(() ->
                new ExploreShadowRequestContext(
                        "request:ip9-test",
                        "correlation:ip9-test",
                        null,
                        Instant.parse("2026-07-19T01:00:00Z"),
                        Instant.parse("2026-07-19T01:00:00Z"),
                        new ProducerBuildId("ip9-test-build-1")));
        AtomicInteger failingHookCalls = new AtomicInteger();
        DefaultExploreSearchShadowBridge hookFailure = new DefaultExploreSearchShadowBridge(
                validFactory,
                request -> {
                    failingHookCalls.incrementAndGet();
                    throw new IllegalStateException("hook_failure");
                });
        Pageable matchingPageable = PageRequest.of(1, 10);
        assertDoesNotThrow(() -> hookFailure.afterExplore(null, null, matchingPageable, response));
        assertEquals(1, failingHookCalls.get());

        AtomicInteger mismatchedMetadataHookCalls = new AtomicInteger();
        DefaultExploreSearchShadowBridge metadataMismatch = new DefaultExploreSearchShadowBridge(
                validFactory,
                request -> {
                    mismatchedMetadataHookCalls.incrementAndGet();
                    return null;
                });
        assertDoesNotThrow(() -> metadataMismatch.afterExplore(null, null, pageable, response));
        assertEquals(0, mismatchedMetadataHookCalls.get());

        DefaultExploreSearchShadowBridge fatal = new DefaultExploreSearchShadowBridge(
                (keyword, region, requestedPage, legacyResponse) -> {
                    throw new AssertionError("fatal");
                },
                request -> null);
        assertThrows(AssertionError.class, () -> fatal.afterExplore(null, null, pageable, response));
    }

    private static PageResponse<PostDtos.Summary> response() {
        return new PageResponse<>(List.of(new PostDtos.Summary(
                1L,
                "title",
                "KR-SEOUL",
                "서울",
                null,
                0L,
                0L,
                0L,
                new PostDtos.Author(2L, "author", null),
                Instant.parse("2026-07-19T00:00:00Z"))), 1, 10, 1L, 1, true);
    }
}
