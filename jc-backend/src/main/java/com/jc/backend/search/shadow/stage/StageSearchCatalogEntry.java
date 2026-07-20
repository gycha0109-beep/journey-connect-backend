package com.jc.backend.search.shadow.stage;

import java.util.List;

record StageSearchCatalogEntry(
        String sourceId,
        String title,
        List<String> queryTerms,
        List<String> regionTerms,
        double rankingScore,
        String orderingKey) {
    StageSearchCatalogEntry {
        if (sourceId == null || !sourceId.matches("[a-z0-9][a-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("sourceId is invalid");
        }
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        queryTerms = List.copyOf(queryTerms);
        regionTerms = List.copyOf(regionTerms);
        if (!Double.isFinite(rankingScore)) throw new IllegalArgumentException("rankingScore must be finite");
        if (orderingKey == null || orderingKey.isBlank()) throw new IllegalArgumentException("orderingKey is required");
    }
}
