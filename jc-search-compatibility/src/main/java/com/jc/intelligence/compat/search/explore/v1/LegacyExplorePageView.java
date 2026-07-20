package com.jc.intelligence.compat.search.explore.v1;

import java.util.ArrayList;
import java.util.List;

public record LegacyExplorePageView(
        List<LegacyExploreItemView> items, Integer page, Integer size, Long totalElements, Integer totalPages, Boolean last) {
    public LegacyExplorePageView {
        items = items == null ? null : List.copyOf(new ArrayList<>(items));
    }
}
