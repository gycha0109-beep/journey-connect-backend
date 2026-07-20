package com.jc.recommendation.p1.profile;

import com.jc.recommendation.model.event.EventType;
import java.time.Instant;
import java.util.Map;

public final class BehaviorProfilePolicies {
    public static final BehaviorProfilePolicy V1 = new BehaviorProfilePolicy(
            "behavior-profile-policy-v1",
            Instant.parse("2026-07-18T15:00:00Z"),
            90,
            5_000,
            21.0d,
            8.0d,
            0.05d,
            12.0d,
            8,
            2.0d,
            Map.ofEntries(
                    Map.entry(EventType.IMPRESSION, 0.05d),
                    Map.entry(EventType.VIEW, 0.15d),
                    Map.entry(EventType.CLICK, 0.40d),
                    Map.entry(EventType.LIKE, 1.00d),
                    Map.entry(EventType.UNLIKE, -1.00d),
                    Map.entry(EventType.SAVE, 1.50d),
                    Map.entry(EventType.UNSAVE, -1.50d),
                    Map.entry(EventType.SHARE, 1.25d),
                    Map.entry(EventType.FOLLOW, 1.25d),
                    Map.entry(EventType.UNFOLLOW, -1.25d),
                    Map.entry(EventType.HIDE, -2.00d),
                    Map.entry(EventType.REPORT, -3.00d),
                    Map.entry(EventType.SEARCH, 0.00d),
                    Map.entry(EventType.TAG_CLICK, 0.50d),
                    Map.entry(EventType.CREW_JOIN, 1.00d),
                    Map.entry(EventType.CREW_LEAVE, -1.00d)));

    private BehaviorProfilePolicies() {
    }
}
