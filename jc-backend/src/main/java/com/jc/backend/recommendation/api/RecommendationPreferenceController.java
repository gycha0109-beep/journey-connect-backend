package com.jc.backend.recommendation.api;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.recommendation.application.RecommendationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated read/replace API for P1 explicit recommendation preferences. */
@RestController
@RequestMapping("/api/v1/recommendation/preferences")
public final class RecommendationPreferenceController {
    private final RecommendationPreferenceService preferenceService;

    public RecommendationPreferenceController(RecommendationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<RecommendationPreferenceDtos.PreferenceListResponse> find(
            @AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(preferenceService.find(Long.parseLong(token.getSubject())));
    }

    @PutMapping
    public ApiResponse<RecommendationPreferenceDtos.PreferenceListResponse> replace(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody RecommendationPreferenceDtos.ReplaceRequest request) {
        return ApiResponse.ok(preferenceService.replace(
                Long.parseLong(token.getSubject()), request.preferences()));
    }
}
