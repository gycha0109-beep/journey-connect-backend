package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.support.ContractJsonWireV1;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegacyExploreFingerprintV1 {
    private static final String REQUEST_DOMAIN = "journey-connect:legacy-explore-request:v1\n";
    private static final String RESPONSE_DOMAIN = "journey-connect:legacy-explore-response:v1\n";

    private LegacyExploreFingerprintV1() { }

    public static String requestFingerprint(LegacyExploreRequestView request) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("keyword", request.keyword());
        map.put("region", request.region());
        map.put("page", request.page());
        map.put("size", request.size());
        List<Object> sorts = new ArrayList<>();
        for (LegacyExploreSortOrderView sort : request.sortOrders()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("property", sort.property());
            item.put("direction", sort.direction() == null ? null : sort.direction().wireValue());
            sorts.add(item);
        }
        map.put("sortOrders", sorts);
        java.util.TreeMap<String, Object> unsupported = new java.util.TreeMap<>();
        for (Map.Entry<String, List<String>> entry : request.unsupportedParameters().entrySet()) {
            List<String> values = new ArrayList<>(entry.getValue());
            values.sort(String::compareTo);
            unsupported.put(entry.getKey(), values);
        }
        map.put("unsupportedParameters", unsupported);
        return hash(REQUEST_DOMAIN + ContractJsonWireV1.stringify(map));
    }

    public static String responseFingerprint(LegacyExplorePageView page) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        List<Object> items = new ArrayList<>();
        if (page.items() != null) for (LegacyExploreItemView item : page.items()) items.add(itemMap(item));
        map.put("items", items);
        map.put("page", page.page());
        map.put("size", page.size());
        map.put("totalElements", page.totalElements());
        map.put("totalPages", page.totalPages());
        map.put("last", page.last());
        return hash(RESPONSE_DOMAIN + ContractJsonWireV1.stringify(map));
    }

    private static Map<String, Object> itemMap(LegacyExploreItemView item) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.id()); map.put("title", item.title()); map.put("regionCode", item.regionCode());
        map.put("regionName", item.regionName()); map.put("coverImageUrl", item.coverImageUrl());
        map.put("viewCount", item.viewCount()); map.put("likeCount", item.likeCount());
        map.put("bookmarkCount", item.bookmarkCount());
        if (item.author() == null) map.put("author", null); else {
            LinkedHashMap<String, Object> author = new LinkedHashMap<>();
            author.put("id", item.author().id()); author.put("nickname", item.author().nickname());
            author.put("profileImageUrl", item.author().profileImageUrl()); map.put("author", author);
        }
        map.put("createdAt", item.createdAt() == null ? null : item.createdAt().toString());
        return map;
    }

    public static String unavailableFingerprint(String kind) {
        return hash("journey-connect:legacy-explore-unavailable:v1\n" + kind);
    }

    private static String hash(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
