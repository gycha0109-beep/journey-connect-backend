package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Instant;

public record LegacyExploreCompatibilityContext(
        String requestId, String correlationId, String sessionRef, Instant referenceTime, Instant mappedAt,
        ProducerBuildId producerBuildId) { }
