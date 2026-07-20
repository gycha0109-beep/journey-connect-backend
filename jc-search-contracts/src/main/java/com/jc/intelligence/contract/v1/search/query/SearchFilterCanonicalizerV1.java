package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.support.ContractJsonWireV1;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SearchFilterCanonicalizerV1 {
    private static final String DOMAIN = "journey-connect:search-filter-fingerprint:v1\n";
    private SearchFilterCanonicalizerV1() { }

    public static List<SearchFilterV1> canonicalize(List<SearchFilterV1> filters) {
        if (filters == null || filters.isEmpty()) return List.of();
        List<SearchFilterV1> sorted = new ArrayList<>(filters);
        sorted.sort(Comparator
                .comparing((SearchFilterV1 value) -> value.filterType().wireValue())
                .thenComparing(value -> value.source().wireValue())
                .thenComparing(value -> String.join("\u0000", value.values())));
        List<SearchFilterV1> result = new ArrayList<>();
        Map<SearchFilterType, SearchFilterV1> singleValues = new java.util.EnumMap<>(SearchFilterType.class);
        SearchFilterV1 previous = null;
        for (SearchFilterV1 filter : sorted) {
            SearchChecks.requireNonNull(filter, "filter");
            if (!filter.filterType().multiValued()) {
                SearchFilterV1 existing = singleValues.putIfAbsent(filter.filterType(), filter);
                if (existing != null && !existing.equals(filter)) {
                    throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_FILTER_INVALID,
                            "conflicting single-value filter: " + filter.filterType().wireValue());
                }
            }
            if (!filter.equals(previous)) result.add(filter);
            previous = filter;
        }
        return List.copyOf(result);
    }

    public static String fingerprint(List<SearchFilterV1> filters) {
        List<SearchFilterV1> canonical = canonicalize(filters);
        List<Object> payload = new ArrayList<>();
        for (SearchFilterV1 filter : canonical) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("filterType", filter.filterType().wireValue());
            map.put("values", filter.values());
            map.put("source", filter.source().wireValue());
            map.put("schemaVersion", filter.schemaVersion().value());
            payload.add(map);
        }
        String material = DOMAIN + ContractJsonWireV1.stringify(payload);
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
