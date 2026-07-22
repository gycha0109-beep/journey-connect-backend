package com.jc.data.contract.v1.adapter.recommendation;

import com.jc.data.contract.support.ImmutableContractValuesV1;
import com.jc.data.contract.v1.event.EventFamily;
import com.jc.data.contract.v1.event.EventType;
import com.jc.data.contract.v1.identity.References;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RecommendationP0MappedEventV1(
        EventFamily eventFamily,
        EventType eventType,
        Instant occurredAt,
        References.ActorRef actorRef,
        References.SessionRef sessionRef,
        References.EntityRef entityRef,
        Map<String, Object> payload,
        String sourceRunRef,
        String authorityEvidenceRef) {
    public RecommendationP0MappedEventV1 {
        Objects.requireNonNull(eventFamily, "eventFamily");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorRef, "actorRef");
        Objects.requireNonNull(sessionRef, "sessionRef");
        payload = ImmutableContractValuesV1.copyMap(payload);
    }
}
