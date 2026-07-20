package com.jc.backend.search.shadow;

import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Instant;
import java.util.Objects;

/** Deterministic request-scoped values supplied only by an explicitly assembled shadow configuration. */
public record ExploreShadowRequestContext(
        String requestId,
        String correlationId,
        String sessionRef,
        Instant referenceTime,
        Instant mappedAt,
        ProducerBuildId producerBuildId) {
    public ExploreShadowRequestContext {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(referenceTime, "referenceTime");
        Objects.requireNonNull(mappedAt, "mappedAt");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
        if (mappedAt.isBefore(referenceTime)) {
            throw new IllegalArgumentException("mappedAt must not precede referenceTime");
        }
    }
}
