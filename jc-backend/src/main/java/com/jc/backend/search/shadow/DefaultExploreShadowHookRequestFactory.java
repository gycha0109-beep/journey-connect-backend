package com.jc.backend.search.shadow;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreAuthorView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreItemView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreSortDirection;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreSortOrderView;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pure mapper for test/stage controlled wiring. It is deliberately not registered as a production bean.
 * Legacy output is represented for comparison only and is never reused as runtime candidate input.
 */
public final class DefaultExploreShadowHookRequestFactory implements ExploreShadowHookRequestFactory {
    private final ExploreShadowRequestContextProvider contextProvider;

    public DefaultExploreShadowHookRequestFactory(ExploreShadowRequestContextProvider contextProvider) {
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider");
    }

    @Override
    public SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> create(
            String keyword,
            String region,
            Pageable pageable,
            PageResponse<PostDtos.Summary> legacyResponse) {
        Objects.requireNonNull(pageable, "pageable");
        Objects.requireNonNull(legacyResponse, "legacyResponse");
        Objects.requireNonNull(legacyResponse.items(), "legacyResponse.items");
        if (pageable.getPageNumber() != legacyResponse.page() || pageable.getPageSize() != legacyResponse.size()) {
            throw new IllegalArgumentException("pageable and legacy response metadata must match");
        }
        ExploreShadowRequestContext context = Objects.requireNonNull(contextProvider.current(), "context");

        LegacyExploreRequestView requestView = new LegacyExploreRequestView(
                keyword,
                region,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sortOrders(pageable.getSort()),
                Map.of());
        LegacyExplorePageView pageView = new LegacyExplorePageView(
                legacyResponse.items().stream().map(DefaultExploreShadowHookRequestFactory::itemView).toList(),
                legacyResponse.page(),
                legacyResponse.size(),
                legacyResponse.totalElements(),
                legacyResponse.totalPages(),
                legacyResponse.last());
        LegacyExploreCompatibilityContext compatibilityContext = new LegacyExploreCompatibilityContext(
                context.requestId(),
                context.correlationId(),
                context.sessionRef(),
                context.referenceTime(),
                context.mappedAt(),
                context.producerBuildId());
        SearchShadowContextV1 shadowContext = new SearchShadowContextV1(
                context.requestId(),
                context.correlationId(),
                context.sessionRef(),
                context.referenceTime());
        return new SearchShadowHookRequestV1<>(
                legacyResponse,
                requestView,
                pageView,
                compatibilityContext,
                shadowContext);
    }

    private static LegacyExploreItemView itemView(PostDtos.Summary item) {
        if (item == null) {
            return null;
        }
        PostDtos.Author author = item.author();
        LegacyExploreAuthorView authorView = author == null
                ? null
                : new LegacyExploreAuthorView(author.id(), author.nickname(), author.profileImageUrl());
        return new LegacyExploreItemView(
                item.id(),
                item.title(),
                item.regionCode(),
                item.regionName(),
                item.coverImageUrl(),
                item.viewCount(),
                item.likeCount(),
                item.bookmarkCount(),
                authorView,
                item.createdAt());
    }

    private static List<LegacyExploreSortOrderView> sortOrders(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return List.of();
        }
        return sort.stream()
                .map(order -> new LegacyExploreSortOrderView(
                        order.getProperty(),
                        order.isAscending() ? LegacyExploreSortDirection.ASC : LegacyExploreSortDirection.DESC))
                .toList();
    }
}
