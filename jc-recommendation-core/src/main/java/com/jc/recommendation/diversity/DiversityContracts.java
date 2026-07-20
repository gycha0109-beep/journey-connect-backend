package com.jc.recommendation.diversity;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.feature.FeatureGroup;
import com.jc.recommendation.model.feature.FeatureStatus;
import com.jc.recommendation.model.ranking.RankedCandidate;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DiversityContracts {
    static final List<DiversityDimension> DIMENSIONS = List.of(
            DiversityDimension.DUPLICATE_GROUP, DiversityDimension.AUTHOR,
            DiversityDimension.REGION, DiversityDimension.THEME
    );
    private DiversityContracts() { }

    public static void validatePolicy(DiversityPolicy policy) {
        nonBlank(policy.policyVersion(), "policyVersion");
        if (!policy.expectedRankingPolicyVersion().equals("ranking-v1")) throw new IllegalArgumentException("expectedRankingPolicyVersion must be ranking-v1");
        if (!policy.expectedScorePolicyVersion().equals("score-composition-v1")) throw new IllegalArgumentException("expectedScorePolicyVersion must be score-composition-v1");
        if (policy.maximumCandidateCount() != CandidateLimitMaximum.VALUE) throw new IllegalArgumentException("maximumCandidateCount must match MAX_CANDIDATES_TO_SCORE");
        inRange(policy.exposureWindowSize(), "exposureWindowSize", 1, CandidateLimitMaximum.VALUE);
        inRange(policy.maxPromotionDistance(), "maxPromotionDistance", 0, CandidateLimitMaximum.VALUE - 1);
        inRange(policy.maxDemotionDistance(), "maxDemotionDistance", 0, CandidateLimitMaximum.VALUE - 1);
        for (DiversityDimension dimension : DIMENSIONS) inRange(policy.exposureCaps().get(dimension), "exposureCaps." + dimension.wireValue(), 1, policy.exposureWindowSize());
        Set<DiversityDimension> order = new HashSet<>(policy.relaxationOrder());
        if (policy.relaxationOrder().size() != DIMENSIONS.size() || order.size() != DIMENSIONS.size() || !order.containsAll(DIMENSIONS)) throw new IllegalArgumentException("relaxationOrder must contain every Diversity dimension exactly once");
        equal("unconstrained_and_observed", policy.missingMetadataBehavior(), "missingMetadataBehavior is invalid");
        equal("forbidden", policy.scoreMutation(), "scoreMutation must be forbidden");
        equal("forbidden", policy.candidateRemoval(), "candidateRemoval must be forbidden");
        equal("forbidden", policy.candidateInsertion(), "candidateInsertion must be forbidden");
        equal("before_cursor_boundary", policy.paginationStage(), "paginationStage is invalid");
        equal("after_diversity", policy.explorationStage(), "explorationStage is invalid");
        if (policy.policyVersion().equals("diversity-v1") && (policy.exposureWindowSize() != 10
                || policy.maxPromotionDistance() != 8 || policy.maxDemotionDistance() != 8
                || policy.exposureCaps().duplicateGroup() != 1 || policy.exposureCaps().author() != 2
                || policy.exposureCaps().region() != 4 || policy.exposureCaps().theme() != 3
                || !policy.relaxationOrder().equals(DiversityPolicies.V1.relaxationOrder()))) {
            throw new IllegalArgumentException("diversity-v1 content is immutable; changed policy content requires a new policyVersion");
        }
    }

    static void validateCandidate(RankedCandidate candidate, int index, String scorePolicyVersion) {
        String label = "baseRankedCandidates[" + index + "]";
        if (candidate.absoluteRank() < 1) throw new IllegalArgumentException(label + ".absoluteRank must be a positive safe integer");
        nonBlank(candidate.entityId(), label + ".entityId");
        rankingType(candidate.entityType(), label + ".entityType");
        unit(candidate.score(), label + ".score");
        positiveUnit(candidate.scoredWeight(), label + ".scoredWeight");
        unit(candidate.neutralFilledWeight(), label + ".neutralFilledWeight");
        if (Math.abs(candidate.scoredWeight() + candidate.neutralFilledWeight() - 1.0) > 1e-12) throw new IllegalArgumentException(label + " weights must sum to 1");
        if (!candidate.scorePolicyVersion().equals(scorePolicyVersion)) throw new IllegalArgumentException(label + ".scorePolicyVersion mismatch");
        unit(candidate.sortKey().score(), label + ".sortKey.score");
        unit(candidate.sortKey().neutralFilledWeight(), label + ".sortKey.neutralFilledWeight");
        if (candidate.sortKey().entityTypeRank() < 0 || candidate.sortKey().entityTypeRank() > 3) throw new IllegalArgumentException(label + ".sortKey.entityTypeRank must be a safe integer in 0..3");
        if (!candidate.sortKey().entityId().equals(candidate.entityId())) throw new IllegalArgumentException(label + ".sortKey.entityId must match candidate.entityId");
        if (Double.doubleToRawLongBits(candidate.sortKey().neutralFilledWeight()) != Double.doubleToRawLongBits(candidate.neutralFilledWeight())) throw new IllegalArgumentException(label + ".sortKey.neutralFilledWeight must match candidate");
        double expectedScore = candidate.score() == 0.0d ? 0.0d : candidate.score();
        if (Double.doubleToRawLongBits(candidate.sortKey().score()) != Double.doubleToRawLongBits(expectedScore)) throw new IllegalArgumentException(label + ".sortKey.score must match canonical Ranking score");
    }

    static void validateMetadata(DiversityCandidateMetadata metadata, int index) {
        String label = "candidateMetadata[" + index + "]";
        nonBlank(metadata.entityId(), label + ".entityId");
        rankingType(metadata.entityType(), label + ".entityType");
        nullableNonBlank(metadata.authorId(), label + ".authorId");
        feature(metadata.primaryRegionFeatureId(), FeatureGroup.REGION, label + ".primaryRegionFeatureId");
        feature(metadata.primaryThemeFeatureId(), FeatureGroup.THEME, label + ".primaryThemeFeatureId");
        nullableNonBlank(metadata.duplicateGroupId(), label + ".duplicateGroupId");
    }

    private static void feature(String value, FeatureGroup group, String label) {
        if (value == null) return;
        nonBlank(value, label);
        var feature = FeatureVocabularyV1.getFeatureById(value);
        if (feature.status() != FeatureStatus.ACTIVE || feature.group() != group) throw new IllegalArgumentException(label + " must be an active " + group.wireValue() + " FeatureId");
    }
    private static void rankingType(RecommendationEntityType value, String label) {
        if (value == RecommendationEntityType.USER) throw new IllegalArgumentException(label + " must be a ranking entity type");
    }
    private static void nullableNonBlank(String value, String label) { if (value != null) nonBlank(value, label); }
    private static void nonBlank(String value, String label) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label + " must be nonblank"); }
    private static void unit(double value, String label) { if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(label + " must be finite in 0..1"); }
    private static void positiveUnit(double value, String label) { unit(value, label); if (value <= 0) throw new IllegalArgumentException(label + " must be greater than zero"); }
    private static void inRange(int value, String label, int minimum, int maximum) { if (value < minimum || value > maximum) throw new IllegalArgumentException(label + " must be a safe integer in " + minimum + ".." + maximum); }
    private static void equal(String expected, String actual, String message) { if (!expected.equals(actual)) throw new IllegalArgumentException(message); }
    private static final class CandidateLimitMaximum { private static final int VALUE = 100; private CandidateLimitMaximum() { } }
}
