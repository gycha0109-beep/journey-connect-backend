package com.jc.recommendation.p1.profile;

import com.jc.recommendation.model.event.EventType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public record BehaviorProfileEvent(
        String eventId,
        EventType eventType,
        Instant occurredAt,
        List<String> featureIds) {

    public BehaviorProfileEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(featureIds, "featureIds");
        if (!eventId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("eventId format is invalid");
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String featureId : featureIds) {
            if (featureId == null || featureId.isBlank()) {
                throw new IllegalArgumentException("featureIds must not contain blank values");
            }
            normalized.add(featureId);
        }
        featureIds = List.copyOf(normalized);
    }
}
