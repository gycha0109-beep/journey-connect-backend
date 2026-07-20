package com.jc.intelligence.production.search.v1;

import java.time.Instant;
import java.util.Objects;

public record SearchDocumentSourceV1(
        long sourcePostId,
        long sourceVersion,
        Long regionId,
        String regionReference,
        String placeReference,
        String title,
        String body,
        ProjectionVisibilityStatus visibilityStatus,
        ProjectionPublicationStatus publicationStatus,
        ProjectionModerationStatus moderationStatus,
        ProjectionDeletionStatus deletionStatus,
        boolean operationalExcluded,
        Instant sourceUpdatedAt) {
    public SearchDocumentSourceV1 {
        if (sourcePostId < 1 || sourceVersion < 1) throw new IllegalArgumentException("source identifiers must be positive");
        if (regionId != null && regionId < 1) throw new IllegalArgumentException("regionId must be positive when present");
        if (regionReference != null && !regionReference.matches("[a-z0-9][a-z0-9-]{0,79}")) throw new IllegalArgumentException("regionReference invalid");
        if (placeReference != null && (!placeReference.matches("place:[1-9][0-9]{0,18}") || placeReference.length()>64)) throw new IllegalArgumentException("placeReference must be opaque");
        if (title == null || body == null) throw new IllegalArgumentException("source text is required");
        Objects.requireNonNull(visibilityStatus, "visibilityStatus"); Objects.requireNonNull(publicationStatus, "publicationStatus");
        Objects.requireNonNull(moderationStatus, "moderationStatus"); Objects.requireNonNull(deletionStatus, "deletionStatus"); Objects.requireNonNull(sourceUpdatedAt, "sourceUpdatedAt");
    }
}
