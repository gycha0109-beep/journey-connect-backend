package com.jc.backend.database;

import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** Holds the verified JWT subject for the lifetime of one servlet request. */
@Component
public final class DatabaseRequestIdentity {

    private final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    public Scope open(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Long previous = currentUserId.get();
        if (previous != null && previous.longValue() != userId) {
            throw new IllegalStateException("Database request identity is already bound to another user");
        }
        currentUserId.set(userId);
        return new Scope(previous);
    }

    public OptionalLong currentUserId() {
        Long value = currentUserId.get();
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    public final class Scope implements AutoCloseable {
        private final Long previous;
        private boolean closed;

        private Scope(Long previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                currentUserId.remove();
            } else {
                currentUserId.set(previous);
            }
        }
    }
}
