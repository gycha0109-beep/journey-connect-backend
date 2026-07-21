package com.jc.data.contract.v1.event;

import java.util.Arrays;
import java.util.Optional;

public enum EventFamily {
    USER_BEHAVIOR("user_behavior"),
    CONTENT_LIFECYCLE("content_lifecycle"),
    AI_ANALYSIS("ai_analysis"),
    SEARCH_RUNTIME("search_runtime"),
    RECOMMENDATION_RUNTIME("recommendation_runtime"),
    EXPERIMENT_RUNTIME("experiment_runtime"),
    ADMIN_AUDIT("admin_audit"),
    TRIP_PLANNER_RUNTIME("trip_planner_runtime"),
    DATA_QUALITY("data_quality");

    private final String wireValue;

    EventFamily(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static Optional<EventFamily> fromWire(String wireValue) {
        return Arrays.stream(values()).filter(value -> value.wireValue.equals(wireValue)).findFirst();
    }
}
