package com.jc.backend.notification;

import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<NotificationDtos.Item> list(long recipientId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) safePage * safeSize;

        Long totalValue = jdbc.queryForObject(
                "select count(*) from public.user_notifications where recipient_id = ?",
                Long.class,
                recipientId);
        long total = totalValue == null ? 0L : totalValue;

        List<NotificationDtos.Item> items = jdbc.query(
                """
                select n.id,
                       n.type,
                       n.target_type,
                       n.target_id,
                       n.read_at,
                       n.created_at,
                       a.id as actor_id,
                       a.display_name as actor_nickname,
                       a.profile_image_url as actor_profile_image_url
                from public.user_notifications n
                left join public.app_users a on a.id = n.actor_id
                where n.recipient_id = ?
                order by n.created_at desc, n.id desc
                limit ? offset ?
                """,
                (rs, rowNum) -> item(rs),
                recipientId,
                safeSize,
                offset);

        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        boolean last = totalPages == 0 || safePage + 1 >= totalPages;
        return new PageResponse<>(items, safePage, safeSize, total, totalPages, last);
    }

    public NotificationDtos.UnreadCount unreadCount(long recipientId) {
        Long count = jdbc.queryForObject(
                """
                select count(*)
                from public.user_notifications
                where recipient_id = ? and read_at is null
                """,
                Long.class,
                recipientId);
        return new NotificationDtos.UnreadCount(count == null ? 0L : count);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public NotificationDtos.UpdateResult markRead(long recipientId, long notificationId) {
        int updated = jdbc.update(
                """
                update public.user_notifications
                set read_at = coalesce(read_at, current_timestamp)
                where id = ? and recipient_id = ?
                """,
                notificationId,
                recipientId);
        if (updated == 0) {
            throw new DomainException(
                    HttpStatus.NOT_FOUND,
                    "NOTIFICATION_NOT_FOUND",
                    "알림을 찾을 수 없습니다.");
        }
        return new NotificationDtos.UpdateResult(updated);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public NotificationDtos.UpdateResult markAllRead(long recipientId) {
        int updated = jdbc.update(
                """
                update public.user_notifications
                set read_at = current_timestamp
                where recipient_id = ? and read_at is null
                """,
                recipientId);
        return new NotificationDtos.UpdateResult(updated);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void postLiked(long actorId, long postId) {
        jdbc.update(
                """
                insert into public.user_notifications(
                    recipient_id,
                    actor_id,
                    type,
                    target_type,
                    target_id,
                    dedupe_key
                )
                select p.author_id,
                       ?,
                       'post_like',
                       'post',
                       p.id,
                       ?
                from public.posts p
                where p.id = ?
                  and p.author_id <> ?
                on conflict (dedupe_key) do nothing
                """,
                actorId,
                "post_like:" + postId + ":" + actorId,
                postId,
                actorId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void postCommented(long actorId, long recipientId, long postId, long commentId) {
        insertPostEvent(actorId, recipientId, postId, "post_comment", "post_comment:" + commentId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void commentReplied(long actorId, long recipientId, long postId, long replyCommentId) {
        insertPostEvent(actorId, recipientId, postId, "comment_reply", "comment_reply:" + replyCommentId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void crewApplication(
            long actorId,
            long recipientId,
            long crewId,
            long applicationId,
            Instant eventAt) {
        insertCrewEvent(
                actorId,
                recipientId,
                crewId,
                applicationId,
                eventAt,
                "crew_application");
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void crewApproved(
            long actorId,
            long recipientId,
            long crewId,
            long applicationId,
            Instant eventAt) {
        insertCrewEvent(
                actorId,
                recipientId,
                crewId,
                applicationId,
                eventAt,
                "crew_approved");
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void crewRejected(
            long actorId,
            long recipientId,
            long crewId,
            long applicationId,
            Instant eventAt) {
        insertCrewEvent(
                actorId,
                recipientId,
                crewId,
                applicationId,
                eventAt,
                "crew_rejected");
    }

    private void insertPostEvent(
            long actorId,
            long recipientId,
            long postId,
            String type,
            String dedupeKey) {
        if (actorId == recipientId) {
            return;
        }
        jdbc.update(
                """
                insert into public.user_notifications(
                    recipient_id,
                    actor_id,
                    type,
                    target_type,
                    target_id,
                    dedupe_key
                ) values (?, ?, ?, 'post', ?, ?)
                on conflict (dedupe_key) do nothing
                """,
                recipientId,
                actorId,
                type,
                postId,
                dedupeKey);
    }

    private void insertCrewEvent(
            long actorId,
            long recipientId,
            long crewId,
            long applicationId,
            Instant eventAt,
            String type) {
        if (actorId == recipientId) {
            return;
        }
        if (eventAt == null) {
            throw new IllegalArgumentException("crew notification eventAt must not be null");
        }
        String dedupeKey = type + ":" + applicationId + ":" + eventAt;
        jdbc.update(
                """
                insert into public.user_notifications(
                    recipient_id,
                    actor_id,
                    type,
                    target_type,
                    target_id,
                    dedupe_key
                ) values (?, ?, ?, 'crew', ?, ?)
                on conflict (dedupe_key) do nothing
                """,
                recipientId,
                actorId,
                type,
                crewId,
                dedupeKey);
    }

    private NotificationDtos.Item item(ResultSet rs) throws SQLException {
        Object actorIdValue = rs.getObject("actor_id");
        NotificationDtos.Actor actor = null;
        if (actorIdValue instanceof Number actorId) {
            actor = new NotificationDtos.Actor(
                    actorId.longValue(),
                    rs.getString("actor_nickname"),
                    rs.getString("actor_profile_image_url"));
        }
        return new NotificationDtos.Item(
                rs.getLong("id"),
                rs.getString("type"),
                rs.getString("target_type"),
                rs.getLong("target_id"),
                actor,
                instant(rs, "read_at"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
