package com.jc.backend.recommendation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/** API contracts for authenticated recommendation behavior capture. */
public final class RecommendationBehaviorDtos {

    private RecommendationBehaviorDtos() {}

    public enum EventType {
        VIEW,
        CLICK,
        SHARE,
        HIDE,
        REPORT
    }

    public record EventRequest(
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
            String eventId,
            @NotBlank @Size(max = 160) String idempotencyKey,
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
            String runId,
            @NotNull EventType eventType,
            @NotNull @Positive Long postId,
            @NotNull Instant occurredAt,
            Map<String, Object> metadata) {
    }

    public record EventResponse(
            String eventId,
            String status) {
    }
}
