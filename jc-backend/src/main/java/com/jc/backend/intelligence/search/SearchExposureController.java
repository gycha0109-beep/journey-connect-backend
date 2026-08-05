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
public final class SearchExposureController {

    private final SearchExposureService exposureService;

    public SearchExposureController(SearchExposureService exposureService) {
        this.exposureService = exposureService;
    }

    @PostMapping("/exposures")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SearchExposureDtos.BatchResponse> record(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody SearchExposureDtos.BatchRequest request) {
        return ApiResponse.created(exposureService.record(
                Long.parseLong(token.getSubject()), token.getId(), request));
    }
}
