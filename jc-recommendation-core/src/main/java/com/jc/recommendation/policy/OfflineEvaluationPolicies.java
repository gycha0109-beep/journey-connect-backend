package com.jc.recommendation.policy;

import com.jc.recommendation.model.event.EventType;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

public final class OfflineEvaluationPolicies {
    public static final OfflineEvaluationPolicy V1 = new OfflineEvaluationPolicy(
            "offline-evaluation-v1",
            Instant.parse("2026-07-01T00:00:00Z"),
            "recommendation-exposure-v1",
            "recommendation-trace-v1",
            "ranking-v3",
            "ranking-cursor-v3",
            "event-weight-v1",
            "same_key_same_payload_dedupe_conflict_error",
            "unique_entity_id_within_recommendation_run",
            "latest_preceding_exposure_once",
            "ranking_v3_cursor_to_exhaustion",
            "same_candidate_and_metadata_snapshots",
            List.of(5, 10, 20),
            30,
            100,
            5,
            windows(),
            30,
            30,
            0.8,
            1.0,
            0,
            "descriptive_observed_support_only",
            "forbidden",
            "forbidden"
    );

    private OfflineEvaluationPolicies() {
    }

    private static EnumMap<EventType, Long> windows() {
        EnumMap<EventType, Long> result = new EnumMap<>(EventType.class);
        long shortWindow = 1_800_000L;
        long longWindow = 604_800_000L;
        result.put(EventType.VIEW, shortWindow);
        result.put(EventType.CLICK, shortWindow);
        result.put(EventType.TAG_CLICK, shortWindow);
        for (EventType type : new EventType[]{EventType.LIKE, EventType.UNLIKE, EventType.SAVE, EventType.UNSAVE,
                EventType.SHARE, EventType.FOLLOW, EventType.UNFOLLOW, EventType.HIDE, EventType.REPORT,
                EventType.CREW_JOIN, EventType.CREW_LEAVE}) {
            result.put(type, longWindow);
        }
        return result;
    }
}
