package com.jc.backend.recommendation.application;

import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.config.RecommendationProperties.Mode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/** Enforces fail-closed rollout configuration and deterministic CANARY allocation. */
@Component
public final class RecommendationModeDecider implements SmartInitializingSingleton {

    private final RecommendationProperties properties;

    public RecommendationModeDecider(RecommendationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        properties.validate();
        if (properties.getMode() == Mode.LIVE) {
            throw new IllegalStateException("Recommendation LIVE is not released; use OFF, SHADOW, or CANARY");
        }
    }

    public boolean shouldRunHomeShadow(Long userId, boolean firstPage) {
        return properties.getMode() == Mode.SHADOW
                && validUser(userId)
                && firstPage;
    }

    public boolean shouldServeHomeCanary(Long userId) {
        if (properties.getMode() != Mode.CANARY || !validUser(userId)) {
            return false;
        }
        return bucket(userId.longValue()) < properties.getCanaryAllocationBasisPoints();
    }

    public boolean isCanaryMode() {
        return properties.getMode() == Mode.CANARY;
    }

    private int bucket(long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getCanaryCursorSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal(("journey-connect:canary-allocation:v1:"
                            + properties.getCanaryReleaseId() + ":" + userId)
                    .getBytes(StandardCharsets.UTF_8));
            long positive = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong() & Long.MAX_VALUE;
            return (int) (positive % 10000L);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to calculate CANARY allocation", exception);
        }
    }

    private static boolean validUser(Long userId) {
        return userId != null && userId.longValue() > 0;
    }
}
