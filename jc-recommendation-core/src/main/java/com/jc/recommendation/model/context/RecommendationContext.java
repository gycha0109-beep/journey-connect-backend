package com.jc.recommendation.model.context;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RecommendationContext(
        String contextId,
        ContextSurface surface,
        ContextScope scope,
        Instant createdAt,
        Instant expiresAt,
        List<ContextClause> clauses,
        ContextSchemaVersion schemaVersion
) {
    public RecommendationContext {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(createdAt, "createdAt");
        clauses = List.copyOf(clauses);
        Objects.requireNonNull(schemaVersion, "schemaVersion");
    }
}
