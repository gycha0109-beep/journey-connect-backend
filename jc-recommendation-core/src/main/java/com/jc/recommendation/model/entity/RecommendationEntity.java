package com.jc.recommendation.model.entity;

import java.time.Instant;
import java.util.Objects;

public record RecommendationEntity(
        String id,
        RecommendationEntityType entityType,
        String sourceId,
        String authorId,
        Instant createdAt,
        RecommendationEntityStatus status,
        RecommendationEntityVisibility visibility,
        EngagementRawData engagement
) {
    public RecommendationEntity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(visibility, "visibility");
    }
}
