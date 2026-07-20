package com.jc.backend.recommendation;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.entity.EngagementRawData;
import com.jc.recommendation.model.entity.RecommendationEntity;
import com.jc.recommendation.model.entity.RecommendationEntityStatus;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.entity.RecommendationEntityVisibility;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** canonical Journey Connect vocabulary를 Java Core v1 feature vocabulary로 제한 변환합니다. */
@Component
public class RecommendationCoreInputMapper {

    private static final Map<String, FeatureKey> TAG_FEATURES = Map.of(
            "food", new FeatureKey(FeatureGroup.THEME, "food"),
            "cafe", new FeatureKey(FeatureGroup.THEME, "cafe"),
            "nature", new FeatureKey(FeatureGroup.THEME, "nature"),
            "solo-travel", new FeatureKey(FeatureGroup.COMPANION, "solo"),
            "couple-trip", new FeatureKey(FeatureGroup.COMPANION, "couple"),
            "family-trip", new FeatureKey(FeatureGroup.COMPANION, "family"));

    public RecommendationCoreCandidate map(RecommendationCandidateRow row) {
        String entityId = Long.toString(row.postId());
        List<EntityFeature> features = features(entityId, row);
        String regionFeatureId = features.stream()
                .map(EntityFeature::featureId)
                .filter(value -> value.startsWith("region:"))
                .findFirst()
                .orElse(null);
        String themeFeatureId = features.stream()
                .map(EntityFeature::featureId)
                .filter(value -> value.startsWith("theme:"))
                .findFirst()
                .orElse(null);
        String authorId = Long.toString(row.authorId());
        String duplicateGroupId = "post:" + entityId;

        RecommendationEntity entity = new RecommendationEntity(
                entityId,
                RecommendationEntityType.POST,
                "journey-connect:posts",
                authorId,
                row.createdAt(),
                RecommendationEntityStatus.ACTIVE,
                visibility(row.visibility()),
                new EngagementRawData(
                        row.viewCount(),
                        row.likeCount(),
                        row.bookmarkCount(),
                        0.0));
        DiversityCandidateMetadata diversity = new DiversityCandidateMetadata(
                entityId,
                RecommendationEntityType.POST,
                authorId,
                regionFeatureId,
                themeFeatureId,
                duplicateGroupId);
        ExplorationCandidateMetadata exploration = new ExplorationCandidateMetadata(
                entityId,
                RecommendationEntityType.POST,
                authorId,
                regionFeatureId,
                themeFeatureId,
                duplicateGroupId,
                row.recentExposureCount());
        return new RecommendationCoreCandidate(entity, row.publishedAt(), features, diversity, exploration);
    }

    public List<RecommendationCoreCandidate> mapAll(List<RecommendationCandidateRow> rows) {
        return rows.stream().map(this::map).toList();
    }

    private List<EntityFeature> features(String entityId, RecommendationCandidateRow row) {
        Map<String, EntityFeature> unique = new LinkedHashMap<>();
        FeatureKey region = regionFeature(row.regionSlug());
        if (region != null) {
            add(unique, entityId, region, row.publishedAt());
        }
        for (String tag : row.tagSlugs()) {
            FeatureKey feature = TAG_FEATURES.get(tag);
            if (feature != null) {
                add(unique, entityId, feature, row.publishedAt());
            }
        }
        return List.copyOf(new ArrayList<>(unique.values()));
    }

    private void add(
            Map<String, EntityFeature> result,
            String entityId,
            FeatureKey key,
            Instant updatedAt) {
        String featureId = FeatureVocabularyV1.getFeatureByGroupAndKey(key.group(), key.key()).id();
        result.putIfAbsent(featureId, new EntityFeature(
                entityId,
                featureId,
                1.0,
                1.0,
                FeatureSource.SYSTEM,
                FeatureValidationStatus.ACCEPTED,
                updatedAt));
    }

    private RecommendationEntityVisibility visibility(String value) {
        return switch (value) {
            case "public" -> RecommendationEntityVisibility.PUBLIC;
            case "followers" -> RecommendationEntityVisibility.FOLLOWERS;
            case "private" -> RecommendationEntityVisibility.PRIVATE;
            default -> throw new IllegalArgumentException("Unknown post visibility: " + value);
        };
    }

    private FeatureKey regionFeature(String slug) {
        if (slug == null) {
            return null;
        }
        if (slug.equals("kr-seoul") || slug.startsWith("kr-seoul-")) {
            return new FeatureKey(FeatureGroup.REGION, "seoul");
        }
        if (slug.equals("kr-busan") || slug.startsWith("kr-busan-")) {
            return new FeatureKey(FeatureGroup.REGION, "busan");
        }
        if (slug.equals("kr-jeju") || slug.startsWith("kr-jeju-")) {
            return new FeatureKey(FeatureGroup.REGION, "jeju");
        }
        if (slug.equals("kr-gangwon") || slug.startsWith("kr-gangwon-")) {
            return new FeatureKey(FeatureGroup.REGION, "gangwon");
        }
        if (slug.equals("kr-gyeongju") || slug.startsWith("kr-gyeongju-")) {
            return new FeatureKey(FeatureGroup.REGION, "gyeongju");
        }
        return null;
    }

    private record FeatureKey(FeatureGroup group, String key) {
    }
}
