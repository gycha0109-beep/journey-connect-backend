package com.jc.backend.crew;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crews")
public class CrewController {

    private final CrewService crewService;

    public CrewController(CrewService crewService) {
        this.crewService = crewService;
    }

    @GetMapping
    ApiResponse<PageResponse<CrewDtos.View>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(crewService.list(pageable));
    }

    @GetMapping("/{crewId}")
    ApiResponse<CrewDtos.View> detail(@PathVariable Long crewId) {
        return ApiResponse.ok(crewService.detail(crewId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CrewDtos.View> create(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody CrewDtos.CreateRequest request) {
        return ApiResponse.created(crewService.create(userId(token), request));
    }

    @PostMapping("/{crewId}/join")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CrewDtos.ApplicationView> join(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long crewId) {
        return ApiResponse.created(crewService.join(userId(token), crewId));
    }

    @DeleteMapping("/{crewId}/join")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelJoin(@AuthenticationPrincipal Jwt token, @PathVariable Long crewId) {
        crewService.cancelJoin(userId(token), crewId);
    }

    @GetMapping("/{crewId}/applications")
    ApiResponse<PageResponse<CrewDtos.ApplicationView>> applications(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long crewId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(crewService.applications(userId(token), crewId, pageable));
    }

    @PatchMapping("/{crewId}/applications/{applicationId}")
    ApiResponse<CrewDtos.ApplicationView> review(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long crewId,
            @PathVariable Long applicationId,
            @Valid @RequestBody CrewDtos.ReviewRequest request) {
        return ApiResponse.ok(crewService.review(
                userId(token),
                crewId,
                applicationId,
                request));
    }

    private long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
