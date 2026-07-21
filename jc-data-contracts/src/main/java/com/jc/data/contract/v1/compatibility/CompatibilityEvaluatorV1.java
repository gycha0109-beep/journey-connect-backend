package com.jc.data.contract.v1.compatibility;

import java.util.Objects;

public final class CompatibilityEvaluatorV1 {
    public static final String PLATFORM_EVENT = "platform-event-v1";
    public static final String USER_BEHAVIOR_SCHEMA = "user-behavior-event-v1";
    public static final String DATA_EVENT_CONSUMER = "data-event-consumer-v1";
    public static final String P0_RECOMMENDATION_EVENT = "recommendation-behavior-event-v1";

    public CompatibilityClassification classify(CompatibilityRequestV1 request) {
        Objects.requireNonNull(request, "request");
        if (P0_RECOMMENDATION_EVENT.equals(request.sourceContractVersion())) {
            return CompatibilityClassification.MIGRATION_REQUIRED;
        }
        if (!PLATFORM_EVENT.equals(request.sourceContractVersion())) {
            return CompatibilityClassification.MIGRATION_REQUIRED;
        }
        if (!USER_BEHAVIOR_SCHEMA.equals(request.schemaVersion())) {
            return CompatibilityClassification.INCOMPATIBLE_SCHEMA;
        }
        if (request.unknownRequiredEnum()) {
            return CompatibilityClassification.INCOMPATIBLE_REQUIRED_ENUM;
        }
        if (!DATA_EVENT_CONSUMER.equals(request.consumerVersion())) {
            return CompatibilityClassification.UNSUPPORTED_CONSUMER_VERSION;
        }
        return request.unknownOptionalFields()
                ? CompatibilityClassification.COMPATIBLE_WITH_IGNORED_OPTIONAL_FIELDS
                : CompatibilityClassification.COMPATIBLE;
    }
}
