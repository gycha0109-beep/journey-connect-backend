package com.jc.recommendation.model.score;

import java.util.Objects;

public record ScoreComponentPolicyVersions(
        String contextMatch,
        String interestMatch,
        String freshness,
        String popularity
) {
    public ScoreComponentPolicyVersions {
        Objects.requireNonNull(contextMatch, "contextMatch");
        Objects.requireNonNull(interestMatch, "interestMatch");
        Objects.requireNonNull(freshness, "freshness");
        Objects.requireNonNull(popularity, "popularity");
    }
}
