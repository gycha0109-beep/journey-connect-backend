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
public class AdminPostService {

    private static final Set<String> MODERATION_STATUSES = Set.of("visible", "hidden");
    private static final Set<String> VISIBILITIES = Set.of("public", "followers", "private");
    private static final int PREVIEW_LENGTH = 1000;

    private final AdminAuthorizationGuard guard;
    private final JdbcTemplate jdbc;

    public AdminPostService(AdminAuthorizationGuard guard, JdbcTemplate jdbc) {
        this.guard = guard;
        this.jdbc = jdbc;
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
    public PageResponse<AdminDtos.PostSummary> list(
            String moderationStatus,
            String visibility,
            String search,
            int page,
            int size) {
        guard.requireActiveAdmin();
        AdminQueryPolicy.PageBounds bounds = AdminQueryPolicy.page(page, size);
        String normalizedModeration = AdminQueryPolicy.optionalValue(
                moderationStatus, MODERATION_STATUSES, "moderationStatus");
        String normalizedVisibility = AdminQueryPolicy.optionalValue(
                visibility, VISIBILITIES, "visibility");
        String normalizedSearch = AdminQueryPolicy.search(search);

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (normalizedModeration != null) {
            where.append(" and p.moderation_status = ?");
            args.add(normalizedModeration);
        }
        if (normalizedVisibility != null) {
            where.append(" and p.visibility = ?");
            args.add(normalizedVisibility);
        }
        if (normalizedSearch != null) {
            where.append(" and (")
                    .append("position(lower(?) in lower(p.title)) > 0 ")
                    .append("or position(lower(?) in lower(p.content)) > 0 ")
                    .append("or position(lower(?) in lower(author.username)) > 0 ")
                    .append("or position(lower(?) in lower(author.display_name)) > 0");
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            args.add(normalizedSearch);
            Long numeric = numeric(normalizedSearch);
            if (numeric != null) {
                where.append(" or p.id = ? or p.author_id = ?");
                args.add(numeric);
                args.add(numeric);
            }
            where.append(')');
        }

        long total = count(
                "select count(*) from public.posts p join public.app_users author on author.id = p.author_id"
                        + where,
                args);
        List<Object> queryArgs = new ArrayList<>();
        queryArgs.add(PREVIEW_LENGTH);
        queryArgs.add(PREVIEW_LENGTH);
        queryArgs.addAll(args);
        queryArgs.add(bounds.size());
        queryArgs.add(bounds.offset());

        List<AdminDtos.PostSummary> items = jdbc.query(
                "select p.id, p.author_id, author.display_name as author_display_name, p.title, "
                        + "left(p.content, ?) as content_preview, char_length(p.content) > ? as content_truncated, "
                        + "p.visibility, p.status, p.moderation_status, p.created_at, p.updated_at, "
                        + "p.moderated_at, p.deleted_at, p.purge_after "
                        + "from public.posts p join public.app_users author on author.id = p.author_id"
                        + where
                        + " order by p.created_at desc, p.id desc limit ? offset ?",
                (rs, row) -> new AdminDtos.PostSummary(
                        rs.getLong("id"),
                        rs.getLong("author_id"),
                        rs.getString("author_display_name"),
                        rs.getString("title"),
                        rs.getString("content_preview"),
                        rs.getBoolean("content_truncated"),
                        rs.getString("visibility"),
                        rs.getString("status"),
                        rs.getString("moderation_status"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("updated_at")),
                        instant(rs.getTimestamp("moderated_at")),
                        instant(rs.getTimestamp("deleted_at")),
                        instant(rs.getTimestamp("purge_after"))),
                queryArgs.toArray());

        return page(items, bounds, total);
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
    public AdminDtos.PostDetail detail(long postId) {
        guard.requireActiveAdmin();
        return findDetail(AdminQueryPolicy.targetId(postId));
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN)
    public AdminDtos.CommandResult hide(long postId, AdminDtos.CommandRequest request) {
        return transition(postId, request, "hidden", "admin_hide_post");
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN)
    public AdminDtos.CommandResult restore(long postId, AdminDtos.CommandRequest request) {
        return transition(postId, request, "visible", "admin_restore_post");
    }

    private AdminDtos.CommandResult transition(
            long postId,
            AdminDtos.CommandRequest request,
            String desiredState,
            String function) {
        guard.requireActiveAdmin();
        postId = AdminQueryPolicy.targetId(postId);
        String reason = AdminQueryPolicy.reason(request == null ? null : request.reason());
        PostState before = findState(postId);
        if (before == null) {
            throw AdminQueryPolicy.notFound();
        }
        if (desiredState.equals(before.moderationStatus())) {
            return result(postId, desiredState, false, before.updatedAt());
        }

        Boolean changed = jdbc.queryForObject(
                "select public." + function + "_command(?, ?)",
                Boolean.class,
                postId,
                reason);

        PostState after = findState(postId);
        return result(postId, after.moderationStatus(), Boolean.TRUE.equals(changed), after.updatedAt());
    }

    private AdminDtos.PostDetail findDetail(long postId) {
        List<AdminDtos.PostDetail> matches = jdbc.query(
                "select p.id, p.author_id, author.username as author_username, "
                        + "author.display_name as author_display_name, p.title, left(p.content, ?) as content_preview, "
                        + "char_length(p.content) > ? as content_truncated, p.visibility, p.status, "
                        + "p.moderation_status, p.created_at, p.updated_at, p.moderated_at, p.deleted_at, p.purge_after "
                        + "from public.posts p join public.app_users author on author.id = p.author_id where p.id = ?",
                (rs, row) -> new AdminDtos.PostDetail(
                        rs.getLong("id"),
                        rs.getLong("author_id"),
                        rs.getString("author_username"),
                        rs.getString("author_display_name"),
                        rs.getString("title"),
                        rs.getString("content_preview"),
                        rs.getBoolean("content_truncated"),
                        rs.getString("visibility"),
                        rs.getString("status"),
                        rs.getString("moderation_status"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("updated_at")),
                        instant(rs.getTimestamp("moderated_at")),
                        instant(rs.getTimestamp("deleted_at")),
                        instant(rs.getTimestamp("purge_after"))),
                PREVIEW_LENGTH,
                PREVIEW_LENGTH,
                postId);
        if (matches.size() != 1) {
            throw AdminQueryPolicy.notFound();
        }
        return matches.getFirst();
    }

    private PostState findState(long postId) {
        List<PostState> matches = jdbc.query(
                "select moderation_status, updated_at from public.posts where id = ?",
                (rs, row) -> new PostState(
                        rs.getString("moderation_status"),
                        instant(rs.getTimestamp("updated_at"))),
                postId);
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

    private record PostState(String moderationStatus, Instant updatedAt) {}
}
