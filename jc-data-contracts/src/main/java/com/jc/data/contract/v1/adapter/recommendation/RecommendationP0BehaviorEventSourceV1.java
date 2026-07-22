package com.jc.data.contract.v1.adapter.recommendation;

import com.jc.data.contract.support.ImmutableContractValuesV1;
import java.time.Instant;
import java.util.Map;

public record RecommendationP0BehaviorEventSourceV1(
        String eventId,
        String idempotencyKey,
        String schemaVersion,
        String payloadFingerprint,
        byte[] canonicalPayload,
        Long userId,
        String sessionId,
        String runId,
        String eventTypeWire,
        String entityType,
        String entityKey,
        Long sourceEntityId,
        Instant occurredAt,
        Instant receivedAt,
        Map<String, Object> metadata) {

    public RecommendationP0BehaviorEventSourceV1 {
        canonicalPayload = canonicalPayload == null ? null : canonicalPayload.clone();
        metadata = metadata == null ? Map.of() : ImmutableContractValuesV1.copyMap(metadata);
    }

    @Override
    public byte[] canonicalPayload() {
        return canonicalPayload == null ? null : canonicalPayload.clone();
    }
}
