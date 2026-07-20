package com.jc.backend.intelligence.compat.recommendation;

import com.jc.intelligence.contract.v1.identity.EntityRef;

public final class RecommendationEntityRefAdapterV1 {
    public EntityRef adaptPostId(long postId) {
        if (postId <= 0L) {
            throw new IllegalArgumentException("postId must be positive");
        }
        return new EntityRef("post:" + postId);
    }

    public EntityRef adaptNumericCoreEntityId(String coreEntityId) {
        if (coreEntityId == null
                || !coreEntityId.equals(coreEntityId.trim())
                || !coreEntityId.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("coreEntityId must be a positive decimal string");
        }
        try {
            return adaptPostId(Long.parseLong(coreEntityId));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("coreEntityId is outside the positive long range", exception);
        }
    }
}
