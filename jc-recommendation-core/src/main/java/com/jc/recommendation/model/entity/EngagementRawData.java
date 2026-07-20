package com.jc.recommendation.model.entity;

public record EngagementRawData(
        double viewCount,
        double likeCount,
        double saveCount,
        double shareCount
) {
}
