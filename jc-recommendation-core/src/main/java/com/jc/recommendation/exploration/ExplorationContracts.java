package com.jc.recommendation.exploration;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationQualityComponent;
import com.jc.recommendation.model.exploration.ExplorationSeedAlgorithm;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureStatus;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.policy.CandidateLimitPolicies;
import com.jc.recommendation.policy.ExplorationPolicy;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ExplorationContracts {
    static final List<RecommendationEntityType> RANKING_TYPES = List.of(
            RecommendationEntityType.POST,
            RecommendationEntityType.JOURNEY,
            RecommendationEntityType.PLACE,
            RecommendationEntityType.CREW
    );
    static final List<ExplorationQualityComponent> QUALITY_COMPONENTS = List.of(
            ExplorationQualityComponent.FRESHNESS,
            ExplorationQualityComponent.POPULARITY
    );

    private ExplorationContracts() {
    }

    static void validatePolicy(ExplorationPolicy policy) {
        nonBlank(policy.policyVersion(), "policyVersion");
        if (!policy.expectedRankingPolicyVersion().equals("ranking-v2")) {
            throw new IllegalArgumentException("expectedRankingPolicyVersion must be ranking-v2");
        }
        if (!policy.expectedScorePolicyVersion().equals("score-composition-v1")) {
            throw new IllegalArgumentException("expectedScorePolicyVersion mismatch");
        }
        nonBlank(policy.expectedDiversityPolicyVersion(), "expectedDiversityPolicyVersion");
        if (policy.maximumCandidateCount() != CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE) {
            throw new IllegalArgumentException("maximumCandidateCount must match MAX_CANDIDATES_TO_SCORE");
        }
        if (!policy.eligibleEntityTypes().equals(List.of(
                RecommendationEntityType.POST, RecommendationEntityType.JOURNEY))) {
            throw new IllegalArgumentException("eligibleEntityTypes must equal post,journey");
        }
        if (policy.eligibleStatus() != CandidateScoreStatus.NOT_APPLICABLE) {
            throw new IllegalArgumentException("eligibleStatus is invalid");
        }
        if (policy.eligibleNotApplicableReason() != CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT) {
            throw new IllegalArgumentException("eligibleNotApplicableReason is invalid");
        }
        if (!policy.qualityComponents().equals(QUALITY_COMPONENTS)) {
            throw new IllegalArgumentException("qualityComponents must equal freshness,popularity");
        }
        if (policy.qualityWeights().freshness() != 4 || policy.qualityWeights().popularity() != 3) {
            throw new IllegalArgumentException("qualityWeights must remain freshness 4 and popularity 3");
        }
        if (policy.minimumAvailableQualityComponents() != 1) {
            throw new IllegalArgumentException("minimumAvailableQualityComponents must be 1");
        }
        finiteUnit(policy.minimumQualityScore(), "minimumQualityScore");
        if (policy.exposureCountWindowDays() != 30) {
            throw new IllegalArgumentException("exposureCountWindowDays must be 30");
        }
        nonNegative(policy.maximumRecentExposureCount(), "maximumRecentExposureCount");
        if (policy.maximumInsertions() < 0 || policy.maximumInsertions() > 10) {
            throw new IllegalArgumentException("maximumInsertions must be in 0..10");
        }
        validateInsertionRanks(policy.insertionRanks(), policy.maximumInsertions());
        equal("exposure_asc_quality_desc_seed_asc_identity_asc", policy.selectionOrder(), "selectionOrder is invalid");
        if (policy.seedAlgorithm() != ExplorationSeedAlgorithm.FNV1A32_UTF8_V1) {
            throw new IllegalArgumentException("seedAlgorithm is invalid");
        }
        if (policy.maximumSeedUtf8Bytes() != 128) {
            throw new IllegalArgumentException("maximumSeedUtf8Bytes must be 128");
        }
        equal("candidate_key_caps_all_affected_windows", policy.diversityGuard(), "diversityGuard is invalid");
        equal("forbidden", policy.diversityRelaxation(), "diversityRelaxation must be forbidden");
        equal("forbidden", policy.personalizedCandidateRemoval(), "personalizedCandidateRemoval must be forbidden");
        equal("forbidden", policy.explorationScoreImpersonation(), "explorationScoreImpersonation must be forbidden");
        equal("forbidden", policy.hardExcludedResurrection(), "hardExcludedResurrection must be forbidden");
        equal("after_exploration", policy.paginationStage(), "paginationStage is invalid");

        if (policy.policyVersion().equals("exploration-v1")) {
            if (!policy.expectedDiversityPolicyVersion().equals("diversity-v1")) {
                throw new IllegalArgumentException("exploration-v1 expectedDiversityPolicyVersion is immutable");
            }
            if (Double.compare(policy.minimumQualityScore(), 0.6d) != 0
                    || policy.maximumRecentExposureCount() != 2
                    || policy.maximumInsertions() != 2
                    || !policy.insertionRanks().equals(List.of(6, 16))) {
                throw new IllegalArgumentException(
                        "exploration-v1 content is immutable; changed policy content requires a new policyVersion"
                );
            }
        }
    }

    static void validateSeed(String value, int maximumBytes) {
        nonBlank(value, "explorationSeed");
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length < 1 || length > maximumBytes) {
            throw new IllegalArgumentException("explorationSeed must be 1.." + maximumBytes + " UTF-8 bytes");
        }
    }

    static void validateRegionFeature(String featureId, String label) {
        validateFeature(featureId, FeatureGroup.REGION, label);
    }

    static void validateThemeFeature(String featureId, String label) {
        validateFeature(featureId, FeatureGroup.THEME, label);
    }

    static void nonBlank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be nonblank");
        }
    }

    static void nullableNonBlank(String value, String label) {
        if (value != null) {
            nonBlank(value, label);
        }
    }

    static void finiteUnit(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(label + " must be finite in 0..1");
        }
    }

    static void nullableFiniteUnit(Double value, String label) {
        if (value != null) {
            finiteUnit(value.doubleValue(), label);
        }
    }

    static void nonNegative(int value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be a safe integer >= 0");
        }
    }

    static int entityTypeRank(RecommendationEntityType type) {
        int rank = RANKING_TYPES.indexOf(type);
        if (rank < 0) {
            throw new IllegalArgumentException("entityType has unknown value");
        }
        return rank;
    }

    static String identity(RecommendationEntityType type, String entityId) {
        return type.wireValue() + '\u0000' + entityId;
    }

    private static void validateInsertionRanks(List<Integer> ranks, int maximumInsertions) {
        int previous = 1;
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < ranks.size(); index++) {
            Integer rank = ranks.get(index);
            if (rank == null || rank.intValue() < 2 || rank.intValue() > CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE) {
                throw new IllegalArgumentException("insertionRanks[" + index + "] is invalid");
            }
            if (rank.intValue() <= previous || !seen.add(rank)) {
                throw new IllegalArgumentException("insertionRanks must be unique and strictly ascending");
            }
            previous = rank.intValue();
        }
        if (ranks.size() < maximumInsertions) {
            throw new IllegalArgumentException("insertionRanks length must cover maximumInsertions");
        }
    }

    private static void validateFeature(String featureId, FeatureGroup group, String label) {
        if (featureId == null) {
            return;
        }
        nonBlank(featureId, label);
        var feature = FeatureVocabularyV1.getFeatureById(featureId);
        if (feature.status() != FeatureStatus.ACTIVE || feature.group() != group) {
            throw new IllegalArgumentException(label + " must be an active " + group.wireValue() + " FeatureId");
        }
    }

    private static void equal(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(message);
        }
    }
}
