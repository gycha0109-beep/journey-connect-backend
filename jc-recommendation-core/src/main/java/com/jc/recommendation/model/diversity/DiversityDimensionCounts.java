package com.jc.recommendation.model.diversity;

public record DiversityDimensionCounts(int duplicateGroup, int author, int region, int theme) {
    public int get(DiversityDimension dimension) {
        return switch (dimension) {
            case DUPLICATE_GROUP -> duplicateGroup;
            case AUTHOR -> author;
            case REGION -> region;
            case THEME -> theme;
        };
    }
}
