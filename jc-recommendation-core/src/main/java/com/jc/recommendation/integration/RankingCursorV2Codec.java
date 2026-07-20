package com.jc.recommendation.integration;

import com.jc.recommendation.canonical.JsonWire;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RankingCursorV2Codec {
    private static final Set<String> KEYS = Set.of(
            "cursorVersion", "rankingSnapshotId", "metadataSnapshotId", "rankingPolicyVersion",
            "scorePolicyVersion", "componentPolicyVersions", "diversityPolicyVersion",
            "lastAbsoluteRank", "lastEntityType", "lastEntityId"
    );

    private RankingCursorV2Codec() {
    }

    public static String encode(Payload payload) {
        LinkedHashMap<String, Object> versions = versions(payload.componentPolicyVersions());
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("cursorVersion", "ranking-cursor-v2");
        canonical.put("rankingSnapshotId", payload.rankingSnapshotId());
        canonical.put("metadataSnapshotId", payload.metadataSnapshotId());
        canonical.put("rankingPolicyVersion", payload.rankingPolicyVersion());
        canonical.put("scorePolicyVersion", payload.scorePolicyVersion());
        canonical.put("componentPolicyVersions", versions);
        canonical.put("diversityPolicyVersion", payload.diversityPolicyVersion());
        canonical.put("lastAbsoluteRank", payload.lastAbsoluteRank());
        canonical.put("lastEntityType", payload.lastEntityType().wireValue());
        canonical.put("lastEntityId", payload.lastEntityId());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                JsonWire.stringify(canonical).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static Payload decode(String cursor) {
        Map<String, Object> payload = IntegrationCursorSupport.decodeObject(cursor, KEYS);
        if (!"ranking-cursor-v2".equals(payload.get("cursorVersion"))) {
            throw new IllegalArgumentException("cursor version mismatch");
        }
        return new Payload(
                IntegrationCursorSupport.string(payload.get("rankingSnapshotId"), "cursor.rankingSnapshotId"),
                IntegrationCursorSupport.string(payload.get("metadataSnapshotId"), "cursor.metadataSnapshotId"),
                IntegrationCursorSupport.string(payload.get("rankingPolicyVersion"), "cursor.rankingPolicyVersion"),
                IntegrationCursorSupport.string(payload.get("scorePolicyVersion"), "cursor.scorePolicyVersion"),
                IntegrationCursorSupport.versions(payload.get("componentPolicyVersions")),
                IntegrationCursorSupport.string(payload.get("diversityPolicyVersion"), "cursor.diversityPolicyVersion"),
                IntegrationCursorSupport.positiveInteger(payload.get("lastAbsoluteRank"), "cursor.lastAbsoluteRank"),
                IntegrationCursorSupport.rankingEntityType(payload.get("lastEntityType")),
                IntegrationCursorSupport.string(payload.get("lastEntityId"), "cursor.lastEntityId")
        );
    }

    private static LinkedHashMap<String, Object> versions(ScoreComponentPolicyVersions value) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("context_match", value.contextMatch());
        result.put("interest_match", value.interestMatch());
        result.put("freshness", value.freshness());
        result.put("popularity", value.popularity());
        return result;
    }

    public record Payload(
            String rankingSnapshotId,
            String metadataSnapshotId,
            String rankingPolicyVersion,
            String scorePolicyVersion,
            ScoreComponentPolicyVersions componentPolicyVersions,
            String diversityPolicyVersion,
            int lastAbsoluteRank,
            RecommendationEntityType lastEntityType,
            String lastEntityId
    ) {
    }
}
