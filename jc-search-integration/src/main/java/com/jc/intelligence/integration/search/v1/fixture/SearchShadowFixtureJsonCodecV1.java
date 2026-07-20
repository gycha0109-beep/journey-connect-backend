package com.jc.intelligence.integration.search.v1.fixture;

import com.jc.intelligence.contract.support.ContractJsonWireV1;
import com.jc.intelligence.contract.support.StrictContractJsonParserV1;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SearchShadowFixtureJsonCodecV1 {
    private SearchShadowFixtureJsonCodecV1() { }

    public static String write(SearchShadowFixtureCaseV1 fixture) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("scenario", fixture.scenario());
        root.put("mode", fixture.mode());
        root.put("runtimeInputStatus", fixture.runtimeInputStatus());
        root.put("runtimeStatus", fixture.runtimeStatus());
        root.put("topK", fixture.topK());
        root.put("timeout", fixture.timeout());
        root.put("legacyEntityRefs", fixture.legacyEntityRefs());
        root.put("runtimeEntityRefs", fixture.runtimeEntityRefs());
        return ContractJsonWireV1.stringify(root);
    }

    @SuppressWarnings("unchecked")
    public static SearchShadowFixtureCaseV1 read(String json) {
        Object parsed = StrictContractJsonParserV1.parse(json);
        if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("fixture root must be object");
        Map<String, Object> map = (Map<String, Object>) raw;
        return new SearchShadowFixtureCaseV1(
                string(map, "scenario"), string(map, "mode"), string(map, "runtimeInputStatus"),
                string(map, "runtimeStatus"), integer(map, "topK"), bool(map, "timeout"),
                strings(map, "legacyEntityRefs"), strings(map, "runtimeEntityRefs"));
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) throw new IllegalArgumentException(key + " must be string");
        return text;
    }
    private static int integer(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Long number)) throw new IllegalArgumentException(key + " must be integer");
        return Math.toIntExact(number.longValue());
    }
    private static boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException(key + " must be boolean");
        return flag.booleanValue();
    }
    private static List<String> strings(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(key + " must be array");
        ArrayList<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text)) throw new IllegalArgumentException(key + " entries must be strings");
            result.add(text);
        }
        return List.copyOf(result);
    }
}
