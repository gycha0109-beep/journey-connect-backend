package com.jc.backend.notification;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    ApiResponse<PageResponse<NotificationDtos.Item>> list(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notifications.list(userId(token), page, size));
    }

    @GetMapping("/unread-count")
    ApiResponse<NotificationDtos.UnreadCount> unreadCount(@AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(notifications.unreadCount(userId(token)));
    }

    @PatchMapping("/{notificationId}/read")
    ApiResponse<NotificationDtos.UpdateResult> markRead(
            @AuthenticationPrincipal Jwt token,
            @PathVariable long notificationId) {
        return ApiResponse.ok(notifications.markRead(userId(token), notificationId));
    }

    @PatchMapping("/read-all")
    ApiResponse<NotificationDtos.UpdateResult> markAllRead(@AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(notifications.markAllRead(userId(token)));
    }

    private long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
