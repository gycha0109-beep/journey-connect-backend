package com.jc.data.contract.v1.adapter.recommendation;

import com.jc.data.contract.v1.identity.References;
import java.util.Objects;

public record RecommendationP0IdentityBindingV1(
        long sourceUserId,
        References.ActorRef actorRef,
        String mappingVersion) {
    public RecommendationP0IdentityBindingV1 {
        if (sourceUserId <= 0) {
            throw new IllegalArgumentException("sourceUserId must be positive");
        }
        Objects.requireNonNull(actorRef, "actorRef");
        if (!"recommendation-user-subject-binding-v1".equals(mappingVersion)) {
            throw new IllegalArgumentException("unsupported identity mapping version");
        }
    }
}
