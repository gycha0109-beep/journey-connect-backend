package com.jc.backend.admin;

import com.jc.backend.admin.security.AdminAuthorizationGuard;
import com.jc.backend.common.PageResponse;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminReportService {

    private static final Set<String> STATUSES = Set.of("pending", "in_review", "resolved", "rejected");
    private static final Set<String> TARGET_TYPES = Set.of("user", "post", "comment");

    private final AdminAuthorizationGuard guard;
    private final JdbcTemplate jdbc;

    public AdminReportService(AdminAuthorizationGuard guard, JdbcTemplate jdbc) {
        this.guard = guard;
        this.jdbc = jdbc;
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
    public PageResponse<AdminDtos.ReportSummary> list(
            String status,
            String targetType,
            String search,
            int page,
            int size) {
        guard.requireActiveAdmin();
        AdminQueryPolicy.PageBounds bounds = AdminQueryPolicy.page(page, size);
        String normalizedStatus = AdminQueryPolicy.optionalValue(status, STATUSES, "status");
        String normalizedTarget = AdminQueryPolicy.optionalValue(targetType, TARGET_TYPES, "targetType");
        String normalizedSearch = AdminQueryPolicy.search(search);

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (normalizedStatus != null) {
            where.append(" and r.status = ?");
            args.add(normalizedStatus);
        }
        if (normalizedTarget != null) {
            where.append(" and r.target_type = ?");
            args.add(normalizedTarget);
        }
        if (normalizedSearch != null) {
            where.append(" and (")
                    .append("position(lower(?) in lower(r.reason_category)) > 0 ")
                    .append("or position(lower(?) in lower(r.reason_detail)) > 0 ")
                    .append("or position(lower(?) in lower(coalesce(reporter.username, ''))) > 0 ")
                    .append("or position(lower(?) in lower(coalesce(reporter.display_name, ''))) > 0");
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            Long numeric = numeric(normalizedSearch);
            if (numeric != null) {
                where.append(" or r.reporter_id = ? or r.id = ?");
                args.add(numeric);
                args.add(numeric);
            }
            where.append(')');
        }

        long total = count(
                "select count(*) from public.reports r left join public.app_users reporter on reporter.id = r.reporter_id"
                        + where,
                args);

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(bounds.size());
        queryArgs.add(bounds.offset());
        List<AdminDtos.ReportSummary> items = jdbc.query(
                "select r.id, r.reporter_id, reporter.username as reporter_username, r.target_type, "
                        + "r.target_entity_id, r.reason_category, r.reason_detail, r.status, r.created_at, r.handled_at "
                        + "from public.reports r left join public.app_users reporter on reporter.id = r.reporter_id"
                        + where
                        + " order by r.created_at desc, r.id desc limit ? offset ?",
                (rs, row) -> new AdminDtos.ReportSummary(
                        rs.getLong("id"),
                        nullableLong(rs.getObject("reporter_id")),
                        rs.getString("reporter_username"),
                        rs.getString("target_type"),
                        rs.getLong("target_entity_id"),
                        rs.getString("reason_category"),
                        rs.getString("reason_detail"),
                        rs.getString("status"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("handled_at"))),
                queryArgs.toArray());

        return page(items, bounds, total);
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
    public AdminDtos.ReportDetail detail(long reportId) {
        guard.requireActiveAdmin();
        return findDetail(AdminQueryPolicy.targetId(reportId));
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN)
    public AdminDtos.CommandResult resolve(long reportId, AdminDtos.CommandRequest request) {
        return finish(reportId, request, "resolved");
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN)
    public AdminDtos.CommandResult dismiss(long reportId, AdminDtos.CommandRequest request) {
        return finish(reportId, request, "rejected");
    }

    private AdminDtos.CommandResult finish(
            long reportId,
            AdminDtos.CommandRequest request,
            String desiredStatus) {
        guard.requireActiveAdmin();
        reportId = AdminQueryPolicy.targetId(reportId);
        String reason = AdminQueryPolicy.reason(request == null ? null : request.reason());
        ReportState before = findState(reportId);
        if (before == null) {
            throw AdminQueryPolicy.notFound();
        }
        if (desiredStatus.equals(before.status())) {
            return result(reportId, before.status(), false, before.updatedAt());
        }
        if (isTerminal(before.status())) {
            throw AdminQueryPolicy.conflict("이미 다른 상태로 종결된 신고입니다.");
        }

        Boolean changed = jdbc.queryForObject(
                "select public.admin_finish_report_command(?, ?, ?)",
                Boolean.class,
                reportId,
                desiredStatus,
                reason);

        ReportState after = findState(reportId);
        return result(reportId, after.status(), Boolean.TRUE.equals(changed), after.updatedAt());
    }

    private AdminDtos.ReportDetail findDetail(long reportId) {
        List<AdminDtos.ReportDetail> matches = jdbc.query(
                "select r.id, r.reporter_id, reporter.username as reporter_username, "
                        + "reporter.display_name as reporter_display_name, r.target_type, r.target_entity_id, "
                        + "r.reason_category, r.reason_detail, r.status, r.created_at, r.handled_at, r.resolution_note, "
                        + "case r.target_type "
                        + "when 'post' then coalesce(target_post.moderation_status, 'missing') "
                        + "when 'user' then coalesce(target_user.account_status, 'missing') "
                        + "when 'comment' then case when target_comment.id is null then 'missing' "
                        + "when target_comment.moderation_deleted_at is not null then 'hidden' "
                        + "when target_comment.deleted_at is not null then 'deleted' else 'visible' end end as current_target_state "
                        + "from public.reports r "
                        + "left join public.app_users reporter on reporter.id = r.reporter_id "
                        + "left join public.posts target_post on target_post.id = r.target_post_id "
                        + "left join public.app_users target_user on target_user.id = r.target_user_id "
                        + "left join public.comments target_comment on target_comment.id = r.target_comment_id "
                        + "where r.id = ?",
                (rs, row) -> {
                    String status = rs.getString("status");
                    boolean open = !isTerminal(status);
                    return new AdminDtos.ReportDetail(
                            rs.getLong("id"),
                            nullableLong(rs.getObject("reporter_id")),
                            rs.getString("reporter_username"),
                            rs.getString("reporter_display_name"),
                            rs.getString("target_type"),
                            rs.getLong("target_entity_id"),
                            rs.getString("reason_category"),
                            rs.getString("reason_detail"),
                            status,
                            instant(rs.getTimestamp("created_at")),
                            instant(rs.getTimestamp("handled_at")),
                            rs.getString("resolution_note"),
                            rs.getString("current_target_state"),
                            open,
                            open);
                },
                reportId);
        if (matches.size() != 1) {
            throw AdminQueryPolicy.notFound();
        }
        return matches.getFirst();
    }

    private ReportState findState(long reportId) {
        List<ReportState> matches = jdbc.query(
                "select status, updated_at from public.reports where id = ?",
                (rs, row) -> new ReportState(
                        rs.getString("status"),
                        instant(rs.getTimestamp("updated_at"))),
                reportId);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private long count(String sql, List<Object> args) {
        Long value = jdbc.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    private static boolean isTerminal(String status) {
        return "resolved".equals(status) || "rejected".equals(status);
    }

    private static AdminDtos.CommandResult result(long id, String status, boolean changed, Instant updatedAt) {
        return new AdminDtos.CommandResult(id, status, changed, updatedAt);
    }

    private static <T> PageResponse<T> page(
            List<T> items,
            AdminQueryPolicy.PageBounds bounds,
            long total) {
        int totalPages = total == 0 ? 0 : (int) ((total + bounds.size() - 1) / bounds.size());
        boolean last = bounds.page() >= Math.max(0, totalPages - 1);
        return new PageResponse<>(List.copyOf(items), bounds.page(), bounds.size(), total, totalPages, last);
    }

    private static Long numeric(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record ReportState(String status, Instant updatedAt) {}
}
