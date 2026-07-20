package com.jc.backend.recommendation.application;

import com.jc.backend.recommendation.config.RecommendationP1Properties;
import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.persistence.RecommendationHashing;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public final class RecommendationP1ModeDecider implements SmartInitializingSingleton {
    private final RecommendationProperties baseProperties;
    private final RecommendationP1Properties p1Properties;

    public RecommendationP1ModeDecider(
            RecommendationProperties baseProperties,
            RecommendationP1Properties p1Properties) {
        this.baseProperties = baseProperties;
        this.p1Properties = p1Properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        p1Properties.validate();
        if (p1Properties.getMode() == RecommendationP1Properties.Mode.SHADOW
                && baseProperties.getMode() != RecommendationProperties.Mode.SHADOW) {
            throw new IllegalStateException("P1 SHADOW requires base recommendation SHADOW mode");
        }
        if (p1Properties.getMode() == RecommendationP1Properties.Mode.CANARY
                && baseProperties.getMode() != RecommendationProperties.Mode.CANARY) {
            throw new IllegalStateException("P1 CANARY requires base recommendation CANARY mode");
        }
    }

    public boolean shouldRunShadow() {
        return p1Properties.getMode() == RecommendationP1Properties.Mode.SHADOW;
    }

    public boolean shouldServeCanary(long userId) {
        if (p1Properties.getMode() != RecommendationP1Properties.Mode.CANARY) {
            return false;
        }
        String material = p1Properties.getReleaseId() + "|" + userId;
        String digest = RecommendationHashing.sha256(material.getBytes(StandardCharsets.UTF_8));
        long bucket = Long.parseUnsignedLong(digest.substring(0, 8), 16) % 10_000L;
        return bucket < p1Properties.getCanaryAllocationBasisPoints();
    }
}
