package com.jc.backend.admin;

import com.jc.backend.admin.security.AdminActor;
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
public class AdminUserService {

    private static final Set<String> ROLES = Set.of("user", "moderator", "admin");
    private static final Set<String> STATUSES = Set.of("active", "suspended", "withdrawn");

    private final AdminAuthorizationGuard guard;
    private final JdbcTemplate jdbc;

    public AdminUserService(AdminAuthorizationGuard guard, JdbcTemplate jdbc) {
        this.guard = guard;
        this.jdbc = jdbc;
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
    public PageResponse<AdminDtos.UserSummary> list(
            String role,
            String accountStatus,
            String search,
            int page,
            int size) {
        guard.requireActiveAdmin();
        AdminQueryPolicy.PageBounds bounds = AdminQueryPolicy.page(page, size);
        String normalizedRole = AdminQueryPolicy.optionalValue(role, ROLES, "role");
        String normalizedStatus = AdminQueryPolicy.optionalValue(accountStatus, STATUSES, "accountStatus");
        String normalizedSearch = AdminQueryPolicy.search(search);

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (normalizedRole != null) {
            where.append(" and u.role = ?");
            args.add(normalizedRole);
        }
        if (normalizedStatus != null) {
            where.append(" and u.account_status = ?");
            args.add(normalizedStatus);
        }
        if (normalizedSearch != null) {
            where.append(" and (")
                    .append("position(lower(?) in lower(u.email)) > 0 ")
                    .append("or position(lower(?) in lower(u.username)) > 0 ")
                    .append("or position(lower(?) in lower(u.display_name)) > 0");
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            Long numeric = numeric(normalizedSearch);
            if (numeric != null) {
                where.append(" or u.id = ?");
                args.add(numeric);
            }
            where.append(')');
        }

        long total = count("select count(*) from public.app_users u" + where, args);
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(bounds.size());
        queryArgs.add(bounds.offset());
        List<AdminDtos.UserSummary> items = jdbc.query(
                "select u.id, u.email, u.username, u.display_name, u.role, u.account_status, "
                        + "u.created_at, case when u.account_status = 'suspended' then u.updated_at end as suspended_at "
                        + "from public.app_users u"
                        + where
                        + " order by u.created_at desc, u.id desc limit ? offset ?",
                (rs, row) -> new AdminDtos.UserSummary(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getString("account_status"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("suspended_at"))),
                queryArgs.toArray());

        return page(items, bounds, total);
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
    public AdminDtos.UserDetail detail(long userId) {
        guard.requireActiveAdmin();
        return findDetail(userId);
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN)
    public AdminDtos.CommandResult suspend(long userId, AdminDtos.CommandRequest request) {
        AdminActor actor = guard.requireActiveAdmin();
        String reason = AdminQueryPolicy.reason(request == null ? null : request.reason());
        if (actor.adminUserId() == userId) {
            throw AdminQueryPolicy.conflict("관리자는 자기 자신을 정지할 수 없습니다.");
        }
        return transition(userId, reason, "suspended", "admin_suspend_user");
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN)
    public AdminDtos.CommandResult unsuspend(long userId, AdminDtos.CommandRequest request) {
        guard.requireActiveAdmin();
        String reason = AdminQueryPolicy.reason(request == null ? null : request.reason());
        return transition(userId, reason, "active", "admin_restore_user");
    }

    private AdminDtos.CommandResult transition(
            long userId,
            String reason,
            String desiredState,
            String function) {
        UserState before = findState(userId);
        if (before == null) {
            throw AdminQueryPolicy.notFound();
        }
        if (desiredState.equals(before.accountStatus())) {
            return result(userId, desiredState, false, before.updatedAt());
        }
        if ("withdrawn".equals(before.accountStatus())) {
            throw AdminQueryPolicy.conflict("탈퇴 또는 삭제보관 계정은 이 명령으로 복구할 수 없습니다.");
        }
        if ("suspended".equals(desiredState) && !"active".equals(before.accountStatus())) {
            throw AdminQueryPolicy.conflict("활성 계정만 정지할 수 있습니다.");
        }
        if ("active".equals(desiredState) && !"suspended".equals(before.accountStatus())) {
            throw AdminQueryPolicy.conflict("정지 계정만 정지 해제할 수 있습니다.");
        }

        try {
            jdbc.queryForObject(
                    "select public." + function + "(?, ?)",
                    Object.class,
                    userId,
                    reason);
        } catch (DataAccessException exception) {
            UserState current = findState(userId);
            if (current == null) {
                throw AdminQueryPolicy.notFound();
            }
            if (desiredState.equals(current.accountStatus())) {
                return result(userId, desiredState, false, current.updatedAt());
            }
            if ("withdrawn".equals(current.accountStatus())) {
                throw AdminQueryPolicy.conflict("탈퇴 또는 삭제보관 계정은 이 명령으로 복구할 수 없습니다.");
            }
            throw AdminQueryPolicy.conflict("동시 처리로 사용자 상태가 변경됐습니다.");
        }

        UserState after = findState(userId);
        return result(userId, after.accountStatus(), true, after.updatedAt());
    }

    private AdminDtos.UserDetail findDetail(long userId) {
        List<AdminDtos.UserDetail> matches = jdbc.query(
                "select u.id, u.email, u.username, u.display_name, u.role, u.account_status, "
                        + "u.created_at, u.updated_at, "
                        + "case when u.account_status = 'suspended' then u.updated_at end as suspended_at "
                        + "from public.app_users u where u.id = ?",
                (rs, row) -> new AdminDtos.UserDetail(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getString("account_status"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("updated_at")),
                        instant(rs.getTimestamp("suspended_at"))),
                userId);
        if (matches.size() != 1) {
            throw AdminQueryPolicy.notFound();
        }
        return matches.getFirst();
    }

    private UserState findState(long userId) {
        List<UserState> matches = jdbc.query(
                "select account_status, updated_at from public.app_users where id = ?",
                (rs, row) -> new UserState(
                        rs.getString("account_status"),
                        instant(rs.getTimestamp("updated_at"))),
                userId);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private long count(String sql, List<Object> args) {
        Long value = jdbc.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    private static AdminDtos.CommandResult result(long id, String state, boolean changed, Instant updatedAt) {
        return new AdminDtos.CommandResult(id, state, changed, updatedAt);
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

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record UserState(String accountStatus, Instant updatedAt) {}
}
