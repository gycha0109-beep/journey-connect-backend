package com.jc.recommendation.ranking;

import com.jc.recommendation.canonical.JsonWire;
import com.jc.recommendation.canonical.StrictJsonParser;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class RankingCursorCodec {
    private static final Pattern BASE64_URL = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> KEYS = Set.of(
            "cursorVersion", "rankingSnapshotId", "rankingPolicyVersion", "scorePolicyVersion",
            "componentPolicyVersions", "lastAbsoluteRank", "lastEntityType", "lastEntityId"
    );
    private static final Set<String> VERSION_KEYS = Set.of(
            "context_match", "interest_match", "freshness", "popularity"
    );

    private RankingCursorCodec() {
    }

    static String encode(CursorPayload payload) {
        LinkedHashMap<String, Object> versions = new LinkedHashMap<>();
        versions.put("context_match", payload.componentPolicyVersions().contextMatch());
        versions.put("interest_match", payload.componentPolicyVersions().interestMatch());
        versions.put("freshness", payload.componentPolicyVersions().freshness());
        versions.put("popularity", payload.componentPolicyVersions().popularity());

        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("cursorVersion", "ranking-cursor-v1");
        canonical.put("rankingSnapshotId", payload.rankingSnapshotId());
        canonical.put("rankingPolicyVersion", payload.rankingPolicyVersion());
        canonical.put("scorePolicyVersion", payload.scorePolicyVersion());
        canonical.put("componentPolicyVersions", versions);
        canonical.put("lastAbsoluteRank", payload.lastAbsoluteRank());
        canonical.put("lastEntityType", payload.lastEntityType().wireValue());
        canonical.put("lastEntityId", payload.lastEntityId());
        byte[] bytes = JsonWire.stringify(canonical).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static CursorPayload decode(String cursor) {
        RankingContracts.nonBlank(cursor, "cursor");
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
        exactKeys(payload, KEYS, "cursor payload");
        if (!"ranking-cursor-v1".equals(payload.get("cursorVersion"))) {
            throw new IllegalArgumentException("cursor version mismatch");
        }
        String snapshotId = string(payload.get("rankingSnapshotId"), "cursor.rankingSnapshotId");
        String rankingVersion = string(payload.get("rankingPolicyVersion"), "cursor.rankingPolicyVersion");
        String scoreVersion = string(payload.get("scorePolicyVersion"), "cursor.scorePolicyVersion");
        Map<String, Object> versions = object(payload.get("componentPolicyVersions"), "cursor.componentPolicyVersions");
        exactKeys(versions, VERSION_KEYS, "cursor.componentPolicyVersions");
        ScoreComponentPolicyVersions componentVersions = new ScoreComponentPolicyVersions(
                string(versions.get("context_match"), "cursor.componentPolicyVersions.context_match"),
                string(versions.get("interest_match"), "cursor.componentPolicyVersions.interest_match"),
                string(versions.get("freshness"), "cursor.componentPolicyVersions.freshness"),
                string(versions.get("popularity"), "cursor.componentPolicyVersions.popularity")
        );
        int rank = positiveInteger(payload.get("lastAbsoluteRank"), "cursor.lastAbsoluteRank");
        RecommendationEntityType entityType = rankingEntityType(
                string(payload.get("lastEntityType"), "cursor.lastEntityType")
        );
        String entityId = string(payload.get("lastEntityId"), "cursor.lastEntityId");
        return new CursorPayload(
                snapshotId, rankingVersion, scoreVersion, componentVersions, rank, entityType, entityId
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + " must be a plain object");
        }
        return (Map<String, Object>) map;
    }

    private static void exactKeys(Map<String, Object> value, Set<String> keys, String label) {
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

    private static String string(Object value, String label) {
        if (!(value instanceof String string) || string.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be nonblank");
        }
        return string;
    }

    private static int positiveInteger(Object value, String label) {
        if (!(value instanceof Long number) || number.longValue() < 1 || number.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be a positive safe integer");
        }
        return number.intValue();
    }

    private static RecommendationEntityType rankingEntityType(String value) {
        for (RecommendationEntityType type : RankingContracts.RANKING_ENTITY_TYPES) {
            if (type.wireValue().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("cursor.lastEntityType has unknown value");
    }

    record CursorPayload(
            String rankingSnapshotId,
            String rankingPolicyVersion,
            String scorePolicyVersion,
            ScoreComponentPolicyVersions componentPolicyVersions,
            int lastAbsoluteRank,
            RecommendationEntityType lastEntityType,
            String lastEntityId
    ) {
    }
}
