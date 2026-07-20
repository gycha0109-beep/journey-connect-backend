package com.jc.recommendation.policy;

import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.diversity.DiversityExposureCaps;
import java.time.Instant;
import java.util.List;

public final class DiversityPolicies {
    public static final DiversityPolicy V1 = new DiversityPolicy(
            "diversity-v1", Instant.parse("2026-07-01T00:00:00Z"), "ranking-v1",
            "score-composition-v1", CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE, 10, 8, 8,
            new DiversityExposureCaps(1, 2, 4, 3),
            List.of(DiversityDimension.THEME, DiversityDimension.REGION,
                    DiversityDimension.AUTHOR, DiversityDimension.DUPLICATE_GROUP),
            "unconstrained_and_observed", "forbidden", "forbidden", "forbidden",
            "before_cursor_boundary", "after_diversity"
    );
    private DiversityPolicies() { }
}
