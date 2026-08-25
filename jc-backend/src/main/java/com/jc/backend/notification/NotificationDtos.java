package com.jc.backend.notification;

import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record Actor(Long id, String nickname, String profileImageUrl) {}

    public record Item(
            long id,
            String type,
            String targetType,
            long targetId,
            Actor actor,
            Instant readAt,
            Instant createdAt) {
        public boolean read() {
            return readAt != null;
        }
    }

    public record UnreadCount(long count) {}

    public record UpdateResult(long updatedCount) {}
}
