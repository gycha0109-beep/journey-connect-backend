package com.jc.data.contract.v1.event;

import com.jc.data.contract.support.ImmutableContractValuesV1;
import com.jc.data.contract.v1.identity.References;
import com.jc.data.contract.v1.version.Versions;
import java.time.Instant;
import java.util.Map;

public record PlatformEventEnvelopeV1(
        Versions.ContractVersion contractVersion,
        Versions.SchemaVersion schemaVersion,
        Versions.CanonicalizationVersion canonicalizationVersion,
        Versions.ProducerVersion producerVersion,
        Versions.ProducerBuildId producerBuildId,
        References.EventId eventId,
        EventFamily eventFamily,
        EventType eventType,
        Instant occurredAt,
        Instant receivedAt,
        References.ActorRef actorRef,
        References.SessionRef sessionRef,
        References.EntityRef entityRef,
        References.RequestRef requestId,
        References.CorrelationRef correlationId,
        References.CausationRef causationId,
        References.IdempotencyKey idempotencyKey,
        Map<String, Object> payload) {
    public PlatformEventEnvelopeV1 {
        payload = payload == null ? Map.of() : ImmutableContractValuesV1.copyMap(payload);
    }
}
