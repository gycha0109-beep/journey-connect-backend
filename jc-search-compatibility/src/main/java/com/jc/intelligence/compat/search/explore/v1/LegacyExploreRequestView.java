package com.jc.intelligence.compat.search.explore.v1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LegacyExploreRequestView(
        String keyword, String region, Integer page, Integer size,
        List<LegacyExploreSortOrderView> sortOrders, Map<String, List<String>> unsupportedParameters) {
    public LegacyExploreRequestView {
        sortOrders = sortOrders == null ? List.of() : List.copyOf(new ArrayList<>(sortOrders));
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        if (unsupportedParameters != null) {
            for (Map.Entry<String, List<String>> entry : unsupportedParameters.entrySet()) {
                copy.put(entry.getKey(), entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
            }
        }
        unsupportedParameters = java.util.Collections.unmodifiableMap(copy);
    }
}
