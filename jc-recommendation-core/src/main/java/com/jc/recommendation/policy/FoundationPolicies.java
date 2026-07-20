package com.jc.recommendation.policy;

import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.feature.FeatureSource;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

public final class FoundationPolicies {
    private static final Instant EFFECTIVE_FROM = Instant.parse("2026-07-01T00:00:00Z");

    public static final EventWeightPolicy EVENT_WEIGHT_V1 = new EventWeightPolicy(
            "event-weight-v1",
            EFFECTIVE_FROM,
            eventWeights()
    );

    public static final RepeatDecayPolicy REPEAT_DECAY_V1 = new RepeatDecayPolicy(
            "repeat-decay-v1",
            EFFECTIVE_FROM,
            List.of(EventType.VIEW, EventType.CLICK, EventType.SHARE, EventType.TAG_CLICK),
            1.0,
            0.3,
            0.1
    );

    public static final TimeDecayPolicy TIME_DECAY_V1 = new TimeDecayPolicy(
            "time-decay-v1",
            EFFECTIVE_FROM,
            List.of(
                    new TimeDecayBucket("days-0-7", 7.0, 1.0),
                    new TimeDecayBucket("days-8-30", 30.0, 0.8),
                    new TimeDecayBucket("days-31-90", 90.0, 0.5),
                    new TimeDecayBucket("days-91-plus", null, 0.25)
            )
    );

    public static final SaturationPolicy SATURATION_V1 = new SaturationPolicy(
            "saturation-v1",
            EFFECTIVE_FROM,
            10.0
    );

    public static final ColdStartPolicy COLD_START_V1 = new ColdStartPolicy(
            "cold-start-v1",
            EFFECTIVE_FROM,
            new ColdStartPolicy.ExplicitPreferenceWeights(0.4, 0.25, 0.2, 0.15),
            new ColdStartPolicy.EmptyProfileWeights(0.4, 0.35, 0.25)
    );

    public static final SourcePriorityPolicy SOURCE_PRIORITY_V1 = new SourcePriorityPolicy(
            "source-priority-v1",
            EFFECTIVE_FROM,
            List.of(
                    FeatureSource.EXPLICIT,
                    FeatureSource.ADMIN,
                    FeatureSource.SYSTEM,
                    FeatureSource.BEHAVIOR,
                    FeatureSource.AI
            )
    );

    private FoundationPolicies() {
    }

    private static EnumMap<EventType, Double> eventWeights() {
        EnumMap<EventType, Double> weights = new EnumMap<>(EventType.class);
        weights.put(EventType.IMPRESSION, 0.0);
        weights.put(EventType.VIEW, 1.0);
        weights.put(EventType.CLICK, 0.5);
        weights.put(EventType.LIKE, 3.0);
        weights.put(EventType.UNLIKE, -3.0);
        weights.put(EventType.SAVE, 5.0);
        weights.put(EventType.UNSAVE, -5.0);
        weights.put(EventType.SHARE, 5.0);
        weights.put(EventType.FOLLOW, 6.0);
        weights.put(EventType.UNFOLLOW, -6.0);
        weights.put(EventType.HIDE, -8.0);
        weights.put(EventType.REPORT, null);
        weights.put(EventType.SEARCH, 0.0);
        weights.put(EventType.TAG_CLICK, 1.5);
        weights.put(EventType.CREW_JOIN, 6.0);
        weights.put(EventType.CREW_LEAVE, -6.0);
        return weights;
    }
}
