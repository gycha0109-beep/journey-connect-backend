package com.jc.recommendation.vocabulary;

import com.jc.recommendation.model.feature.EntityFeature;
import com.jc.recommendation.model.feature.FeatureDefinition;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureValidationStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FeatureVocabularyV1 {
    private static final List<FeatureDefinition> FEATURES = List.of(
            feature(FeatureGroup.REGION, "seoul", "서울"),
            feature(FeatureGroup.REGION, "busan", "부산"),
            feature(FeatureGroup.REGION, "jeju", "제주"),
            feature(FeatureGroup.REGION, "gangwon", "강원"),
            feature(FeatureGroup.REGION, "gyeongju", "경주"),
            feature(FeatureGroup.THEME, "cafe", "카페"),
            feature(FeatureGroup.THEME, "food", "맛집"),
            feature(FeatureGroup.THEME, "nature", "자연"),
            feature(FeatureGroup.THEME, "night_view", "야경"),
            feature(FeatureGroup.THEME, "culture", "문화"),
            feature(FeatureGroup.THEME, "shopping", "쇼핑"),
            feature(FeatureGroup.THEME, "healing", "힐링"),
            feature(FeatureGroup.PACE, "relaxed", "느긋함"),
            feature(FeatureGroup.PACE, "balanced", "균형"),
            feature(FeatureGroup.PACE, "active", "활동적"),
            feature(FeatureGroup.BUDGET, "low", "저예산"),
            feature(FeatureGroup.BUDGET, "medium", "중간 예산"),
            feature(FeatureGroup.BUDGET, "high", "고예산"),
            feature(FeatureGroup.COMPANION, "solo", "혼자"),
            feature(FeatureGroup.COMPANION, "couple", "커플"),
            feature(FeatureGroup.COMPANION, "friends", "친구"),
            feature(FeatureGroup.COMPANION, "family", "가족"),
            feature(FeatureGroup.COMPANION, "crew", "크루"),
            feature(FeatureGroup.ENVIRONMENT, "indoor", "실내"),
            feature(FeatureGroup.ENVIRONMENT, "outdoor", "야외"),
            feature(FeatureGroup.ENVIRONMENT, "mixed", "혼합"),
            feature(FeatureGroup.TRANSPORT, "walking", "도보"),
            feature(FeatureGroup.TRANSPORT, "public_transit", "대중교통"),
            feature(FeatureGroup.TRANSPORT, "car", "자동차"),
            feature(FeatureGroup.TIME, "morning", "아침"),
            feature(FeatureGroup.TIME, "afternoon", "오후"),
            feature(FeatureGroup.TIME, "evening", "저녁"),
            feature(FeatureGroup.TIME, "night", "밤"),
            feature(FeatureGroup.MOOD, "quiet", "조용함"),
            feature(FeatureGroup.MOOD, "lively", "활기참"),
            feature(FeatureGroup.MOOD, "romantic", "로맨틱"),
            feature(FeatureGroup.MOOD, "local", "로컬"),
            feature(FeatureGroup.ACTIVITY, "eating", "먹기"),
            feature(FeatureGroup.ACTIVITY, "walking", "걷기"),
            feature(FeatureGroup.ACTIVITY, "photography", "사진"),
            feature(FeatureGroup.ACTIVITY, "hiking", "하이킹"),
            feature(FeatureGroup.ACTIVITY, "shopping", "쇼핑")
    );

    private static final Map<String, FeatureDefinition> BY_ID = indexById();
    private static final Map<String, FeatureDefinition> BY_GROUP_AND_KEY = indexByGroupAndKey();

    private FeatureVocabularyV1() {
    }

    public static List<FeatureDefinition> getAllFeatures() {
        return FEATURES;
    }

    public static List<FeatureDefinition> getFeaturesByGroup(FeatureGroup group) {
        return FEATURES.stream().filter(feature -> feature.group() == group).toList();
    }

    public static FeatureDefinition getFeatureById(String featureId) {
        FeatureDefinition feature = BY_ID.get(featureId);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature ID: " + featureId);
        }
        return feature;
    }

    public static FeatureDefinition getFeatureByGroupAndKey(FeatureGroup group, String key) {
        FeatureDefinition feature = BY_GROUP_AND_KEY.get(compoundKey(group, key));
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature: " + group.wireValue() + ":" + key);
        }
        return feature;
    }

    public static boolean isRegisteredFeature(String featureId) {
        return BY_ID.containsKey(featureId);
    }

    public static boolean isEntityFeatureUsable(EntityFeature feature) {
        return isRegisteredFeature(feature.featureId())
                && feature.validationStatus() == FeatureValidationStatus.ACCEPTED;
    }

    private static FeatureDefinition feature(FeatureGroup group, String key, String displayName) {
        return FeatureDefinition.active(group, key, displayName);
    }

    private static Map<String, FeatureDefinition> indexById() {
        Map<String, FeatureDefinition> result = new LinkedHashMap<>();
        for (FeatureDefinition feature : FEATURES) {
            if (result.put(feature.id(), feature) != null) {
                throw new IllegalStateException("Duplicate feature ID: " + feature.id());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, FeatureDefinition> indexByGroupAndKey() {
        Map<String, FeatureDefinition> result = new LinkedHashMap<>();
        for (FeatureDefinition feature : FEATURES) {
            String key = compoundKey(feature.group(), feature.key());
            if (result.put(key, feature) != null) {
                throw new IllegalStateException("Duplicate feature group/key: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static String compoundKey(FeatureGroup group, String key) {
        return group.wireValue() + ":" + key;
    }
}
