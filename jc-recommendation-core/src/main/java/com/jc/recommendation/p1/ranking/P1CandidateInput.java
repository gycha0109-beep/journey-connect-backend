package com.jc.recommendation.p1.ranking;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public record P1CandidateInput(
        String entityId,
        RecommendationEntityType entityType,
        Instant publishedAt,
        List<String> featureIds,
        long viewCount,
        long likeCount,
        long bookmarkCount,
        int recentExposureCount,
        double contextScore,
        DiversityCandidateMetadata diversityMetadata) {

    public P1CandidateInput {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(featureIds, "featureIds");
        Objects.requireNonNull(diversityMetadata, "diversityMetadata");
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }
        if (viewCount < 0 || likeCount < 0 || bookmarkCount < 0 || recentExposureCount < 0) {
            throw new IllegalArgumentException("candidate counters must be nonnegative");
        }
        if (!Double.isFinite(contextScore) || contextScore < 0.0d || contextScore > 1.0d) {
            throw new IllegalArgumentException("contextScore must be finite and within [0,1]");
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String featureId : featureIds) {
            if (featureId == null || featureId.isBlank()) {
                throw new IllegalArgumentException("featureIds must not contain blank values");
            }
            normalized.add(featureId);
        }
        featureIds = List.copyOf(normalized);
        if (!diversityMetadata.entityId().equals(entityId) || diversityMetadata.entityType() != entityType) {
            throw new IllegalArgumentException("diversity metadata identity mismatch");
        }
    }

    public String identity() {
        return entityType.wireValue() + ":" + entityId;
    }
}
