package com.jc.backend.recommendation.api;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.recommendation.application.RecommendationBehaviorService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated endpoint for run-bound recommendation behavior events. */
@RestController
@RequestMapping("/api/v1/recommendation")
public final class RecommendationBehaviorController {

    private final RecommendationBehaviorService behaviorService;

    public RecommendationBehaviorController(RecommendationBehaviorService behaviorService) {
        this.behaviorService = behaviorService;
    }

    @PostMapping("/events")
    public ApiResponse<RecommendationBehaviorDtos.EventResponse> record(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody RecommendationBehaviorDtos.EventRequest request) {
        long userId = Long.parseLong(token.getSubject());
        return ApiResponse.ok(behaviorService.record(userId, token.getId(), request));
    }
}
