package com.jc.recommendation.integration;

import com.jc.recommendation.canonical.StrictJsonParser;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class IntegrationCursorSupport {
    private static final Pattern BASE64_URL = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> VERSION_KEYS = Set.of(
            "context_match", "interest_match", "freshness", "popularity"
    );

    private IntegrationCursorSupport() {
    }

    static Map<String, Object> decodeObject(String cursor, Set<String> expectedKeys) {
        RankingIntegrationContracts.nonBlank(cursor, "cursor");
        if (!BASE64_URL.matcher(cursor).matches() || cursor.contains("=")) {
            throw new IllegalArgumentException("cursor must be unpadded base64url");
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(cursor);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("cursor base64url decode failed", exception);
        }
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(cursor)) {
            throw new IllegalArgumentException("cursor is not canonical base64url");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("cursor UTF-8 decode failed", exception);
        }
        Object parsed;
        try {
            parsed = StrictJsonParser.parse(text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("cursor JSON parse failed", exception);
        }
        Map<String, Object> payload = object(parsed, "cursor payload");
        exactKeys(payload, expectedKeys, "cursor payload");
        return payload;
    }

    static ScoreComponentPolicyVersions versions(Object value) {
        Map<String, Object> versions = object(value, "cursor.componentPolicyVersions");
        exactKeys(versions, VERSION_KEYS, "cursor.componentPolicyVersions");
        return new ScoreComponentPolicyVersions(
                string(versions.get("context_match"), "cursor.componentPolicyVersions.context_match"),
                string(versions.get("interest_match"), "cursor.componentPolicyVersions.interest_match"),
                string(versions.get("freshness"), "cursor.componentPolicyVersions.freshness"),
                string(versions.get("popularity"), "cursor.componentPolicyVersions.popularity")
        );
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + " must be a plain object");
        }
        return (Map<String, Object>) map;
    }

    static void exactKeys(Map<String, Object> value, Set<String> keys, String label) {
        if (!value.keySet().equals(keys)) {
            for (String key : value.keySet()) {
                if (!keys.contains(key)) {
                    throw new IllegalArgumentException(label + " contains unknown key: " + key);
                }
            }
            for (String key : keys) {
                if (!value.containsKey(key)) {
                    throw new IllegalArgumentException(label + " missing key: " + key);
                }
            }
        }
    }

    static String string(Object value, String label) {
        if (!(value instanceof String string) || string.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be nonblank");
        }
        return string;
    }

    static int positiveInteger(Object value, String label) {
        if (!(value instanceof Long number) || number.longValue() < 1 || number.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be a positive safe integer");
        }
        return number.intValue();
    }

    static RecommendationEntityType rankingEntityType(Object value) {
        String wire = string(value, "cursor.lastEntityType");
        for (RecommendationEntityType type : new RecommendationEntityType[]{
                RecommendationEntityType.POST,
                RecommendationEntityType.JOURNEY,
                RecommendationEntityType.PLACE,
                RecommendationEntityType.CREW
        }) {
            if (type.wireValue().equals(wire)) return type;
        }
        throw new IllegalArgumentException("cursor.lastEntityType has unknown value");
    }
}
