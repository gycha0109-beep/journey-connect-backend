package com.jc.backend.admin;

import com.jc.backend.admin.security.AdminAuthorizationGuard;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
public class AdminDashboardService {

    private static final int RECENT_LIMIT = 5;

    private final AdminAuthorizationGuard guard;
    private final JdbcTemplate jdbc;

    public AdminDashboardService(AdminAuthorizationGuard guard, JdbcTemplate jdbc) {
        this.guard = guard;
        this.jdbc = jdbc;
    }

    public AdminDtos.Dashboard dashboard() {
        guard.requireActiveAdmin();
        long totalUsers = count("select count(*) from public.app_users");
        long activePosts = count("select count(*) from public.posts where status = 'published' and moderation_status = 'visible'");
        long pendingReports = count("select count(*) from public.reports where status = 'pending'");
        long suspendedUsers = count("select count(*) from public.app_users where account_status = 'suspended'");

        List<AdminDtos.RecentReport> reports = jdbc.query(
                "select id, target_type, target_entity_id, reason_category, status, created_at "
                        + "from public.reports order by created_at desc, id desc limit ?",
                (rs, row) -> new AdminDtos.RecentReport(
                        rs.getLong("id"),
                        rs.getString("target_type"),
                        rs.getLong("target_entity_id"),
                        rs.getString("reason_category"),
                        rs.getString("status"),
                        instant(rs.getTimestamp("created_at"))),
                RECENT_LIMIT);

        List<AdminDtos.RecentAdminAction> actions = jdbc.query(
                "select id, actor_username, action_type, target_type, target_entity_id, created_at "
                        + "from public.admin_actions order by created_at desc, id desc limit ?",
                (rs, row) -> new AdminDtos.RecentAdminAction(
                        rs.getLong("id"),
                        rs.getString("actor_username"),
                        rs.getString("action_type"),
                        rs.getString("target_type"),
                        rs.getLong("target_entity_id"),
                        instant(rs.getTimestamp("created_at"))),
                RECENT_LIMIT);

        return new AdminDtos.Dashboard(
                totalUsers,
                activePosts,
                pendingReports,
                suspendedUsers,
                List.copyOf(reports),
                List.copyOf(actions));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
