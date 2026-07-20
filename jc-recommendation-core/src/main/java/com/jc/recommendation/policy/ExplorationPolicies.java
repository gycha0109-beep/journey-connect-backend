package com.jc.recommendation.policy;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exploration.ExplorationQualityComponent;
import com.jc.recommendation.model.exploration.ExplorationQualityWeights;
import com.jc.recommendation.model.exploration.ExplorationSeedAlgorithm;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreStatus;

import java.time.Instant;
import java.util.List;

public final class ExplorationPolicies {
    public static final ExplorationPolicy V1 = new ExplorationPolicy(
            "exploration-v1",
            Instant.parse("2026-07-01T00:00:00Z"),
            "ranking-v2",
            "score-composition-v1",
            "diversity-v1",
            CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE,
            List.of(RecommendationEntityType.POST, RecommendationEntityType.JOURNEY),
            CandidateScoreStatus.NOT_APPLICABLE,
            CandidateScoreNotApplicableReason.NO_ANCHOR_COMPONENT,
            List.of(ExplorationQualityComponent.FRESHNESS, ExplorationQualityComponent.POPULARITY),
            new ExplorationQualityWeights(4, 3),
            1,
            0.6,
            30,
            2,
            2,
            List.of(6, 16),
            "exposure_asc_quality_desc_seed_asc_identity_asc",
            ExplorationSeedAlgorithm.FNV1A32_UTF8_V1,
            128,
            "candidate_key_caps_all_affected_windows",
            "forbidden",
            "forbidden",
            "forbidden",
            "forbidden",
            "after_exploration"
    );

    private ExplorationPolicies() {
    }
}
