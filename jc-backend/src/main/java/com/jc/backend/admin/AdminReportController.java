package com.jc.backend.admin;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService service;

    public AdminReportController(AdminReportService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<AdminDtos.ReportSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(status, targetType, search, page, size));
    }

    @GetMapping("/{reportId}")
    ApiResponse<AdminDtos.ReportDetail> detail(@PathVariable long reportId) {
        return ApiResponse.ok(service.detail(reportId));
    }

    @PostMapping("/{reportId}/resolve")
    ApiResponse<AdminDtos.CommandResult> resolve(
            @PathVariable long reportId,
            @RequestBody AdminDtos.CommandRequest request) {
        return ApiResponse.ok(service.resolve(reportId, request));
    }

    @PostMapping("/{reportId}/dismiss")
    ApiResponse<AdminDtos.CommandResult> dismiss(
            @PathVariable long reportId,
            @RequestBody AdminDtos.CommandRequest request) {
        return ApiResponse.ok(service.dismiss(reportId, request));
    }
}
