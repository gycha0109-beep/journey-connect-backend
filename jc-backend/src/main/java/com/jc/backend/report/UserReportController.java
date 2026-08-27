package com.jc.backend.report;

import com.jc.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
public class UserReportController {

    private final UserReportService reports;

    public UserReportController(UserReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/{postId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<UserReportDtos.CreateResult> reportPost(
            @PathVariable long postId,
            @Valid @RequestBody UserReportDtos.CreateRequest request) {
        return ApiResponse.created(reports.reportPost(postId, request));
    }
}
