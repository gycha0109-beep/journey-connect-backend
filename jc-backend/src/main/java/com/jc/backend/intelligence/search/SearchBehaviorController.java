package com.jc.backend.intelligence.search;

import com.jc.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public final class SearchBehaviorController {

    private final SearchBehaviorService behaviorService;

    public SearchBehaviorController(SearchBehaviorService behaviorService) {
        this.behaviorService = behaviorService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SearchBehaviorDtos.EventResponse> record(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody SearchBehaviorDtos.EventRequest request) {
        return ApiResponse.created(behaviorService.record(
                Long.parseLong(token.getSubject()),
                token.getId(),
                request));
    }
}
