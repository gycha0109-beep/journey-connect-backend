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
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final AdminPostService service;

    public AdminPostController(AdminPostService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<AdminDtos.PostSummary>> list(
            @RequestParam(required = false) String moderationStatus,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(moderationStatus, visibility, search, page, size));
    }

    @GetMapping("/{postId}")
    ApiResponse<AdminDtos.PostDetail> detail(@PathVariable long postId) {
        return ApiResponse.ok(service.detail(postId));
    }

    @PostMapping("/{postId}/hide")
    ApiResponse<AdminDtos.CommandResult> hide(
            @PathVariable long postId,
            @RequestBody AdminDtos.CommandRequest request) {
        return ApiResponse.ok(service.hide(postId, request));
    }

    @PostMapping("/{postId}/restore")
    ApiResponse<AdminDtos.CommandResult> restore(
            @PathVariable long postId,
            @RequestBody AdminDtos.CommandRequest request) {
        return ApiResponse.ok(service.restore(postId, request));
    }
}
