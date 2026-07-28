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
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResponse<AdminDtos.UserSummary>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(role, accountStatus, search, page, size));
    }

    @GetMapping("/{userId}")
    ApiResponse<AdminDtos.UserDetail> detail(@PathVariable long userId) {
        return ApiResponse.ok(service.detail(userId));
    }

    @PostMapping("/{userId}/suspend")
    ApiResponse<AdminDtos.CommandResult> suspend(
            @PathVariable long userId,
            @RequestBody AdminDtos.CommandRequest request) {
        return ApiResponse.ok(service.suspend(userId, request));
    }

    @PostMapping("/{userId}/unsuspend")
    ApiResponse<AdminDtos.CommandResult> unsuspend(
            @PathVariable long userId,
            @RequestBody AdminDtos.CommandRequest request) {
        return ApiResponse.ok(service.unsuspend(userId, request));
    }
}
