package com.jc.backend.intelligence.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class SearchBehaviorDtos {

    private SearchBehaviorDtos() {}

    public enum EventType {
        IMPRESSION,
        VIEW,
        CLICK
    }

    public record EventRequest(
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
            String eventId,
            @NotBlank @Size(max = 160) String idempotencyKey,
            @NotBlank @Size(max = 8192) String resultContextToken,
            @NotNull EventType eventType,
            @NotNull @Positive Long postId,
            @NotNull @Positive Integer absoluteRank,
            @NotNull Instant occurredAt) {}

    public record EventResponse(String eventId, String status) {}
}
