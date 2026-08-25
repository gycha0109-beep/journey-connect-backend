package com.jc.backend.crew;

import com.jc.backend.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crews")
public class CrewTagController {

    private final CrewTagService crewTags;

    public CrewTagController(CrewTagService crewTags) {
        this.crewTags = crewTags;
    }

    @GetMapping("/{crewId}/tags")
    ApiResponse<List<CrewTagDtos.TagView>> list(@PathVariable Long crewId) {
        return ApiResponse.ok(crewTags.list(crewId));
    }

    @PutMapping("/{crewId}/tags")
    ApiResponse<List<CrewTagDtos.TagView>> replace(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long crewId,
            @Valid @RequestBody CrewTagDtos.ReplaceRequest request) {
        return ApiResponse.ok(crewTags.replace(Long.parseLong(token.getSubject()), crewId, request));
    }
}
