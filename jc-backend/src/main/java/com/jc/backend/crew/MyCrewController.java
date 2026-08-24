package com.jc.backend.crew;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/crews")
public class MyCrewController {

    private final CrewService crewService;

    public MyCrewController(CrewService crewService) {
        this.crewService = crewService;
    }

    @GetMapping
    ApiResponse<PageResponse<CrewDtos.MyCrewItem>> list(
            @AuthenticationPrincipal Jwt token,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(crewService.myCrews(userId(token), pageable));
    }

    private long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
