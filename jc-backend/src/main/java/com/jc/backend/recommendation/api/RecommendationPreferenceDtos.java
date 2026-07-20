package com.jc.backend.recommendation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Authenticated explicit preference contracts used for P1 cold-start profiles. */
public final class RecommendationPreferenceDtos {

    private RecommendationPreferenceDtos() {}

    public enum PreferenceKind {
        PREFER,
        AVOID
    }

    public record PreferenceRequest(
            @NotBlank
            @Size(max = 160)
            @Pattern(regexp = "^[a-z][a-z0-9_]*:[a-z0-9][a-z0-9_:-]{0,127}$")
            String featureId,
            @NotNull PreferenceKind preferenceKind,
            @DecimalMin("0.0") @DecimalMax("1.0") double strength) {
    }

    public record ReplaceRequest(
            @NotNull @Size(max = 64) List<@Valid PreferenceRequest> preferences) {
    }

    public record PreferenceResponse(
            String featureId,
            PreferenceKind preferenceKind,
            double strength) {
    }

    public record PreferenceListResponse(
            List<PreferenceResponse> preferences) {
        public PreferenceListResponse {
            preferences = List.copyOf(preferences);
        }
    }
}
