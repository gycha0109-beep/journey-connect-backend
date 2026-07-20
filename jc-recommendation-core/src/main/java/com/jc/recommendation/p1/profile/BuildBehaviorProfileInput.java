package com.jc.recommendation.p1.profile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record BuildBehaviorProfileInput(
        String userId,
        Instant referenceTime,
        List<ExplicitPreference> explicitPreferences,
        List<BehaviorProfileEvent> events,
        BehaviorProfilePolicy policy) {

    public BuildBehaviorProfileInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(referenceTime, "referenceTime");
        explicitPreferences = List.copyOf(Objects.requireNonNull(explicitPreferences, "explicitPreferences"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(policy, "policy");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}
