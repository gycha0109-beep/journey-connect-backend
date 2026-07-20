package com.jc.backend.intelligence.compat.recommendation;

import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import java.time.Duration;
import java.util.Set;

public final class RecommendationP2MetricSemanticsV1 {
    public static final String METRIC_DEFINITION_VERSION = "recommendation-metrics-v1";
    public static final String EVALUATION_POLICY_VERSION = "recommendation-evaluation-policy-v1";
    public static final String DATASET_SCHEMA_VERSION = "recommendation-evaluation-dataset-v1";
    public static final ExposureSourceId ENGAGEMENT_EXPOSURE_SOURCE =
            ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1;
    public static final Duration ENGAGEMENT_ATTRIBUTION_WINDOW = Duration.ofDays(7);
    public static final Set<String> ENGAGEMENT_EVENT_ALLOWLIST =
            Set.of("click", "like", "save", "share");
    public static final String FALLBACK_RUN_STATUS = "fallback";

    private RecommendationP2MetricSemanticsV1() {
    }

    public static boolean isEngagementEvent(String eventType) {
        return ENGAGEMENT_EVENT_ALLOWLIST.contains(eventType);
    }

    public static boolean isExcludedFromEngagement(String eventType) {
        return Set.of("view", "impression", "hide", "report").contains(eventType);
    }
}
