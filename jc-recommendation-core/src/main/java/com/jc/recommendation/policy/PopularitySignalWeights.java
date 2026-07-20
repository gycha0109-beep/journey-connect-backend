package com.jc.recommendation.policy;

public record PopularitySignalWeights(
        double like,
        double save,
        double share
) {
}
