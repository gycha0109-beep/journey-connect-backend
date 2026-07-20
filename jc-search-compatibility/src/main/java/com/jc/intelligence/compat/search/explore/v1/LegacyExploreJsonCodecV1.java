package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.support.ContractJsonWireV1;
import com.jc.intelligence.contract.support.StrictContractJsonParserV1;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegacyExploreJsonCodecV1 {
    private LegacyExploreJsonCodecV1() { }

    public static LegacyExploreFixtureCase readFixtureCase(String json) {
        Map<String, Object> root = object(StrictContractJsonParserV1.parse(json), "root");
        String name = string(root.get("name"), "name", true);
        LegacyExploreRequestView request = readRequest(object(root.get("request"), "request"));
        LegacyExplorePageView page = readPage(object(root.get("page"), "page"));
        Map<String, Object> c = object(root.get("context"), "context");
        LegacyExploreCompatibilityContext context = new LegacyExploreCompatibilityContext(
                string(c.get("requestId"), "requestId", false), string(c.get("correlationId"), "correlationId", false),
                string(c.get("sessionRef"), "sessionRef", false), instant(c.get("referenceTime"), "referenceTime"),
                instant(c.get("mappedAt"), "mappedAt"), new ProducerBuildId(string(c.get("producerBuildId"), "producerBuildId", true)));
        LegacyExploreCompatibilityStatus status = LegacyExploreCompatibilityStatus.fromWire(
                string(root.get("expectedStatus"), "expectedStatus", true));
        String failure = string(root.get("expectedFailureCode"), "expectedFailureCode", false);
        return new LegacyExploreFixtureCase(name, request, page, context, status,
                failure == null ? null : LegacyExploreMappingFailureCode.fromWire(failure));
    }

    public static String writeRequest(LegacyExploreRequestView request) { return ContractJsonWireV1.stringify(requestMap(request)); }
    public static LegacyExploreRequestView readRequestJson(String json) { return readRequest(object(StrictContractJsonParserV1.parse(json), "request")); }
    public static String writePage(LegacyExplorePageView page) { return ContractJsonWireV1.stringify(pageMap(page)); }
    public static LegacyExplorePageView readPageJson(String json) { return readPage(object(StrictContractJsonParserV1.parse(json), "page")); }

    private static LegacyExploreRequestView readRequest(Map<String, Object> map) {
        List<LegacyExploreSortOrderView> sorts = new ArrayList<>();
        for (Object value : array(map.get("sortOrders"), "sortOrders", true)) {
            Map<String, Object> item = object(value, "sortOrder");
            String direction = string(item.get("direction"), "direction", false);
            sorts.add(new LegacyExploreSortOrderView(string(item.get("property"), "property", false),
                    direction == null ? null : LegacyExploreSortDirection.fromWire(direction)));
        }
        LinkedHashMap<String, List<String>> unsupported = new LinkedHashMap<>();
        Object rawUnsupported = map.get("unsupportedParameters");
        if (rawUnsupported != null) {
            for (Map.Entry<String, Object> entry : object(rawUnsupported, "unsupportedParameters").entrySet()) {
                List<String> values = new ArrayList<>();
                for (Object value : array(entry.getValue(), entry.getKey(), false)) values.add(string(value, entry.getKey(), true));
                unsupported.put(entry.getKey(), values);
            }
        }
        return new LegacyExploreRequestView(string(map.get("keyword"), "keyword", false),
                string(map.get("region"), "region", false), integer(map.get("page"), "page", false),
                integer(map.get("size"), "size", false), sorts, unsupported);
    }

    private static LegacyExplorePageView readPage(Map<String, Object> map) {
        List<LegacyExploreItemView> items = null;
        if (map.containsKey("items") && map.get("items") != null) {
            items = new ArrayList<>();
            for (Object value : array(map.get("items"), "items", false)) items.add(readItem(object(value, "item")));
        }
        Long total = longValue(map.get("totalElements"), "totalElements", false);
        return new LegacyExplorePageView(items, integer(map.get("page"), "page", false),
                integer(map.get("size"), "size", false), total, integer(map.get("totalPages"), "totalPages", false),
                bool(map.get("last"), "last", false));
    }

    private static LegacyExploreItemView readItem(Map<String, Object> map) {
        LegacyExploreAuthorView author = null;
        if (map.get("author") != null) {
            Map<String, Object> a = object(map.get("author"), "author");
            author = new LegacyExploreAuthorView(longValue(a.get("id"), "author.id", false),
                    string(a.get("nickname"), "author.nickname", false), string(a.get("profileImageUrl"), "author.profileImageUrl", false));
        }
        return new LegacyExploreItemView(longValue(map.get("id"), "id", false), string(map.get("title"), "title", false),
                string(map.get("regionCode"), "regionCode", false), string(map.get("regionName"), "regionName", false),
                string(map.get("coverImageUrl"), "coverImageUrl", false), longValue(map.get("viewCount"), "viewCount", false),
                longValue(map.get("likeCount"), "likeCount", false), longValue(map.get("bookmarkCount"), "bookmarkCount", false),
                author, instant(map.get("createdAt"), "createdAt", false));
    }

    private static Map<String, Object> requestMap(LegacyExploreRequestView request) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("keyword", request.keyword()); map.put("region", request.region()); map.put("page", request.page()); map.put("size", request.size());
        List<Object> sorts = new ArrayList<>();
        for (LegacyExploreSortOrderView sort : request.sortOrders()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>(); item.put("property", sort.property());
            item.put("direction", sort.direction() == null ? null : sort.direction().wireValue()); sorts.add(item);
        }
        map.put("sortOrders", sorts); map.put("unsupportedParameters", request.unsupportedParameters()); return map;
    }
    private static Map<String, Object> pageMap(LegacyExplorePageView page) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(); List<Object> items = new ArrayList<>();
        if (page.items() != null) for (LegacyExploreItemView item : page.items()) {
            LinkedHashMap<String, Object> i = new LinkedHashMap<>(); i.put("id", item.id()); i.put("title", item.title());
            i.put("regionCode", item.regionCode()); i.put("regionName", item.regionName()); i.put("coverImageUrl", item.coverImageUrl());
            i.put("viewCount", item.viewCount()); i.put("likeCount", item.likeCount()); i.put("bookmarkCount", item.bookmarkCount());
            if (item.author() == null) i.put("author", null); else { LinkedHashMap<String, Object> a = new LinkedHashMap<>();
                a.put("id", item.author().id()); a.put("nickname", item.author().nickname()); a.put("profileImageUrl", item.author().profileImageUrl()); i.put("author", a); }
            i.put("createdAt", item.createdAt() == null ? null : item.createdAt().toString()); items.add(i);
        }
        map.put("items", items); map.put("page", page.page()); map.put("size", page.size());
        map.put("totalElements", page.totalElements()); map.put("totalPages", page.totalPages()); map.put("last", page.last()); return map;
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(field + " must be object");
        return (Map<String, Object>) map;
    }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value, String field, boolean nullAsEmpty) {
        if (value == null && nullAsEmpty) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(field + " must be array");
        return (List<Object>) list;
    }
    private static String string(Object value, String field, boolean required) {
        if (value == null) { if (required) throw new IllegalArgumentException(field + " is required"); return null; }
        if (!(value instanceof String text)) throw new IllegalArgumentException(field + " must be string"); return text;
    }
    private static Integer integer(Object value, String field, boolean required) {
        Long number = longValue(value, field, required);
        if (number == null) return null; return Math.toIntExact(number);
    }
    private static Long longValue(Object value, String field, boolean required) {
        if (value == null) { if (required) throw new IllegalArgumentException(field + " is required"); return null; }
        if (!(value instanceof Number number)) throw new IllegalArgumentException(field + " must be number");
        if (number instanceof Double floating && (!Double.isFinite(floating) || floating.doubleValue() != Math.rint(floating.doubleValue()))) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return number.longValue();
    }
    private static Boolean bool(Object value, String field, boolean required) {
        if (value == null) { if (required) throw new IllegalArgumentException(field + " is required"); return null; }
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(field + " must be boolean"); return result;
    }
    private static Instant instant(Object value, String field) { return instant(value, field, true); }
    private static Instant instant(Object value, String field, boolean required) {
        String text = string(value, field, required); return text == null ? null : Instant.parse(text);
    }
}
