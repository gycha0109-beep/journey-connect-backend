package com.jc.backend.recommendation;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.RecommendationEntity;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.feature.EntityFeature;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** DB projection converted into the contracts consumed by the Java recommendation core. */
public record RecommendationCoreCandidate(
        RecommendationEntity entity,
        Instant publishedAt,
        List<EntityFeature> features,
        DiversityCandidateMetadata diversity,
        ExplorationCandidateMetadata exploration) {

    public RecommendationCoreCandidate {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(publishedAt, "publishedAt");
        features = List.copyOf(Objects.requireNonNull(features, "features"));
        Objects.requireNonNull(diversity, "diversity");
        Objects.requireNonNull(exploration, "exploration");
    }
}
