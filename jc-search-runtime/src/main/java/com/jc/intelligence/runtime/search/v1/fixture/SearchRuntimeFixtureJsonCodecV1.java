package com.jc.intelligence.runtime.search.v1.fixture;

import com.jc.intelligence.contract.support.ContractJsonWireV1;
import com.jc.intelligence.contract.support.StrictContractJsonParserV1;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SearchRuntimeFixtureJsonCodecV1 {
    private SearchRuntimeFixtureJsonCodecV1() { }


    public static String write(SearchRuntimeFixtureCaseV1 fixture) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("scenario", fixture.scenario());
        root.put("rawQuery", fixture.rawQuery());
        root.put("pageSize", fixture.pageSize());
        root.put("maximumCandidateCount", fixture.maximumCandidateCount());
        root.put("retrievalStatus", fixture.retrievalStatus());
        root.put("rankingStatus", fixture.rankingStatus());
        root.put("reverseReranking", fixture.reverseReranking());
        java.util.ArrayList<Object> candidates = new java.util.ArrayList<>();
        for (SearchRuntimeFixtureCandidateV1 candidate : fixture.candidates()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("entityRef", candidate.entityRef());
            item.put("sourceRank", candidate.sourceRank());
            item.put("rankingScore", candidate.rankingScore());
            item.put("orderingKey", candidate.orderingKey());
            item.put("eligibilityDecision", candidate.eligibilityDecision());
            item.put("visibilityDecision", candidate.visibilityDecision());
            candidates.add(item);
        }
        root.put("candidates", candidates);
        return ContractJsonWireV1.stringify(root);
    }

    @SuppressWarnings("unchecked")
    public static SearchRuntimeFixtureCaseV1 read(String json) {
        Object parsed = StrictContractJsonParserV1.parse(json);
        if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("fixture root must be object");
        Map<String, Object> map = (Map<String, Object>) raw;
        List<SearchRuntimeFixtureCandidateV1> candidates = new ArrayList<>();
        Object rawCandidates = map.get("candidates");
        if (!(rawCandidates instanceof List<?> list)) throw new IllegalArgumentException("candidates must be array");
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawCandidate)) throw new IllegalArgumentException("candidate must be object");
            Map<String, Object> candidate = (Map<String, Object>) rawCandidate;
            candidates.add(new SearchRuntimeFixtureCandidateV1(
                    string(candidate, "entityRef"), integer(candidate, "sourceRank"),
                    doubleNullable(candidate.get("rankingScore")), nullableString(candidate.get("orderingKey")),
                    string(candidate, "eligibilityDecision"), string(candidate, "visibilityDecision")));
        }
        return new SearchRuntimeFixtureCaseV1(string(map, "scenario"), nullableString(map.get("rawQuery")),
                integer(map, "pageSize"), integer(map, "maximumCandidateCount"),
                string(map, "retrievalStatus"), string(map, "rankingStatus"),
                bool(map, "reverseReranking"), candidates);
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) throw new IllegalArgumentException(key + " must be string");
        return text;
    }
    private static String nullableString(Object value) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw new IllegalArgumentException("value must be string or null");
        return text;
    }
    private static int integer(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Long number)) throw new IllegalArgumentException(key + " must be integer");
        return Math.toIntExact(number.longValue());
    }
    private static Double doubleNullable(Object value) {
        if (value == null) return null;
        if (value instanceof Long number) return Double.valueOf(number.doubleValue());
        if (value instanceof Double number) return number;
        throw new IllegalArgumentException("rankingScore must be number or null");
    }
    private static boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException(key + " must be boolean");
        return flag.booleanValue();
    }
}
