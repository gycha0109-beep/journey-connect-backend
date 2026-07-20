package com.jc.intelligence.contract.v1.authority;

import java.util.Map;

public final class ExposureSourceRegistryV1 {
    private static final Map<ExposureSourceId, ExposureSourceDefinitionV1> DEFINITIONS = Map.of(
            ExposureSourceId.RECOMMENDATION_GENERAL_EXPOSURE_V1,
            new ExposureSourceDefinitionV1(
                    ExposureSourceId.RECOMMENDATION_GENERAL_EXPOSURE_V1,
                    ExposureSourceStatus.ACTIVE,
                    "recommendation_exposure_event+recommendation_exposure_candidate",
                    "general recommendation page exposure evidence",
                    false,
                    true),
            ExposureSourceId.RECOMMENDATION_BEHAVIOR_IMPRESSION_V1,
            new ExposureSourceDefinitionV1(
                    ExposureSourceId.RECOMMENDATION_BEHAVIOR_IMPRESSION_V1,
                    ExposureSourceStatus.ACTIVE,
                    "recommendation_behavior_event:event_type=impression",
                    "behavior fact; not an automatic experiment denominator",
                    false,
                    true),
            ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1,
            new ExposureSourceDefinitionV1(
                    ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1,
                    ExposureSourceStatus.PROTECTED_AUTHORITY,
                    "recommendation_p2_experiment_exposure",
                    "P2 experiment exposure and evaluation denominator",
                    true,
                    true),
            ExposureSourceId.SEARCH_EXPOSURE_V1,
            new ExposureSourceDefinitionV1(
                    ExposureSourceId.SEARCH_EXPOSURE_V1,
                    ExposureSourceStatus.RESERVED,
                    "not_implemented",
                    "future search exposure evidence",
                    false,
                    false));

    private ExposureSourceRegistryV1() {
    }

    public static ExposureSourceDefinitionV1 definition(ExposureSourceId id) {
        return DEFINITIONS.get(java.util.Objects.requireNonNull(id, "id"));
    }

    public static Map<ExposureSourceId, ExposureSourceDefinitionV1> definitions() {
        return DEFINITIONS;
    }
}
