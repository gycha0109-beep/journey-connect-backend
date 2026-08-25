package com.jc.backend.recommendation.api;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.recommendation.application.CrewRecommendationApiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated read-only API for deterministic Crew recommendations. */
@RestController
@RequestMapping("/api/v1/recommendation/crews")
public final class CrewRecommendationController {

    private final CrewRecommendationApiService service;

    public CrewRecommendationController(CrewRecommendationApiService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<CrewRecommendationDtos.Response> find(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.find(Long.parseLong(token.getSubject()), limit));
    }
}
