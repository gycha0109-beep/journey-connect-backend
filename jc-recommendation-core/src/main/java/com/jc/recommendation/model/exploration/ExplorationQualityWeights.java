package com.jc.recommendation.model.exploration;

public record ExplorationQualityWeights(int freshness, int popularity) {
    public int get(ExplorationQualityComponent component) {
        return switch (component) {
            case FRESHNESS -> freshness;
            case POPULARITY -> popularity;
        };
    }
}
