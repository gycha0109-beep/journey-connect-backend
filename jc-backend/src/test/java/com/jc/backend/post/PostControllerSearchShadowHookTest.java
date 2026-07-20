package com.jc.backend.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.PageResponse;
import com.jc.backend.recommendation.application.RecommendationFeedService;
import com.jc.backend.recommendation.application.RecommendationPostInteractionService;
import com.jc.backend.search.shadow.DefaultExploreSearchShadowBridge;
import com.jc.backend.search.shadow.ExploreSearchShadowBridge;
import com.jc.backend.search.shadow.ExploreShadowHookRequestFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PostControllerSearchShadowHookTest {

    @Test
    void explorePreservesLegacyResponseIdentityAndCallsServiceAndBridgeOnce() {
        PostService service = mock(PostService.class);
        RecommendationFeedService feed = mock(RecommendationFeedService.class);
        RecommendationPostInteractionService interactions = mock(RecommendationPostInteractionService.class);
        ExploreSearchShadowBridge bridge = mock(ExploreSearchShadowBridge.class);
        PostController controller = new PostController(service, feed, interactions, bridge);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(2, 5);
        PageResponse<PostDtos.Summary> legacy = pageResponse();
        when(service.explore("seoul", "KR-SEOUL", pageable)).thenReturn(legacy);

        ApiResponse<PageResponse<PostDtos.Summary>> response =
                controller.explore("seoul", "KR-SEOUL", pageable);

        assertSame(legacy, response.data());
        assertEquals(ApiResponse.ok(legacy), response);
        verify(service).explore("seoul", "KR-SEOUL", pageable);
        verify(bridge).afterExplore("seoul", "KR-SEOUL", pageable, legacy);
        verifyNoInteractions(feed, interactions);
    }

    @Test
    void mvcSerializationAndPageableContractRemainExact() throws Exception {
        PostService service = mock(PostService.class);
        RecommendationFeedService feed = mock(RecommendationFeedService.class);
        RecommendationPostInteractionService interactions = mock(RecommendationPostInteractionService.class);
        ExploreSearchShadowBridge bridge = mock(ExploreSearchShadowBridge.class);
        PostController controller = new PostController(service, feed, interactions, bridge);
        PageResponse<PostDtos.Summary> legacy = pageResponse();
        when(service.explore(eq("서울"), eq("KR-SEOUL"), any(Pageable.class))).thenReturn(legacy);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();

        MvcResult result = mvc.perform(get("/api/v1/explore")
                        .param("keyword", "서울")
                        .param("region", "KR-SEOUL")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(mapper.writeValueAsString(ApiResponse.ok(legacy)), result.getResponse().getContentAsString());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).explore(eq("서울"), eq("KR-SEOUL"), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());
        verify(bridge).afterExplore(eq("서울"), eq("KR-SEOUL"), any(Pageable.class), eq(legacy));
    }

    @Test
    void bridgeRuntimeFailureIsContainedInsideBridgeAndResponseRemainsLegacy() {
        PostService service = mock(PostService.class);
        RecommendationFeedService feed = mock(RecommendationFeedService.class);
        RecommendationPostInteractionService interactions = mock(RecommendationPostInteractionService.class);
        ExploreShadowHookRequestFactory failingFactory = (keyword, region, pageable, response) -> {
            throw new IllegalStateException("factory_failed");
        };
        ExploreSearchShadowBridge bridge = new DefaultExploreSearchShadowBridge(
                failingFactory,
                request -> {
                    throw new AssertionError("hook must not be reached");
                });
        PostController controller = new PostController(service, feed, interactions, bridge);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        PageResponse<PostDtos.Summary> legacy = pageResponse();
        when(service.explore(null, null, pageable)).thenReturn(legacy);

        ApiResponse<PageResponse<PostDtos.Summary>> response = controller.explore(null, null, pageable);

        assertSame(legacy, response.data());
        verify(service).explore(null, null, pageable);
    }

    @Test
    void legacyServiceExceptionIsPreservedAndBridgeIsNotCalled() {
        PostService service = mock(PostService.class);
        RecommendationFeedService feed = mock(RecommendationFeedService.class);
        RecommendationPostInteractionService interactions = mock(RecommendationPostInteractionService.class);
        ExploreSearchShadowBridge bridge = mock(ExploreSearchShadowBridge.class);
        PostController controller = new PostController(service, feed, interactions, bridge);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        RuntimeException failure = new IllegalArgumentException("legacy_failure");
        when(service.explore("bad", null, pageable)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> controller.explore("bad", null, pageable));

        assertSame(failure, thrown);
        verify(service).explore("bad", null, pageable);
        verify(bridge, never()).afterExplore(any(), any(), any(), any());
    }

    private static PageResponse<PostDtos.Summary> pageResponse() {
        PostDtos.Summary item = new PostDtos.Summary(
                11L,
                "Seoul walk",
                "KR-SEOUL",
                "서울",
                "https://example.test/cover.jpg",
                10L,
                2L,
                3L,
                new PostDtos.Author(7L, "traveler", null),
                Instant.parse("2026-07-19T01:02:03Z"));
        return new PageResponse<>(List.of(item), 2, 5, 11L, 3, true);
    }
}
