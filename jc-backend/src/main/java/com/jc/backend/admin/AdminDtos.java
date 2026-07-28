package com.jc.backend.admin;

import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {}

    public record CommandRequest(String reason) {}

    public record CommandResult(
            long targetId,
            String state,
            boolean changed,
            Instant updatedAt) {}

    public record Dashboard(
            long totalUsers,
            long activePostCount,
            long pendingReportCount,
            long suspendedUserCount,
            List<RecentReport> recentReports,
            List<RecentAdminAction> recentAdminActions) {}

    public record RecentReport(
            long reportId,
            String targetType,
            long targetId,
            String reasonCategory,
            String status,
            Instant createdAt) {}

    public record RecentAdminAction(
            long actionId,
            String actorUsername,
            String actionType,
            String targetType,
            long targetId,
            Instant createdAt) {}

    public record ReportSummary(
            long reportId,
            Long reporterId,
            String reporterUsername,
            String targetType,
            long targetId,
            String reasonCategory,
            String reasonDetail,
            String status,
            Instant createdAt,
            Instant handledAt) {}

    public record ReportDetail(
            long reportId,
            Long reporterId,
            String reporterUsername,
            String reporterDisplayName,
            String targetType,
            long targetId,
            String reasonCategory,
            String reasonDetail,
            String status,
            Instant createdAt,
            Instant handledAt,
            String resolutionNote,
            String currentTargetState,
            boolean canResolve,
            boolean canDismiss) {}

    public record PostSummary(
            long postId,
            long authorId,
            String authorDisplayName,
            String title,
            String contentPreview,
            boolean contentTruncated,
            String visibility,
            String contentStatus,
            String moderationStatus,
            Instant createdAt,
            Instant updatedAt,
            Instant hiddenAt,
            Instant deletedAt,
            Instant purgeAfter) {}

    public record PostDetail(
            long postId,
            long authorId,
            String authorUsername,
            String authorDisplayName,
            String title,
            String contentPreview,
            boolean contentTruncated,
            String visibility,
            String contentStatus,
            String moderationStatus,
            Instant createdAt,
            Instant updatedAt,
            Instant hiddenAt,
            Instant deletedAt,
            Instant purgeAfter) {}

    public record UserSummary(
            long userId,
            String email,
            String username,
            String displayName,
            String role,
            String accountStatus,
            Instant createdAt,
            Instant suspendedAt) {}

    public record UserDetail(
            long userId,
            String email,
            String username,
            String displayName,
            String role,
            String accountStatus,
            Instant createdAt,
            Instant updatedAt,
            Instant suspendedAt) {}
}
